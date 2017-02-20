(ns blog-web.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [io.pedestal.test :as test]
            [io.pedestal.interceptor :refer [interceptor]]
            [hiccup.core :as hc]))

;(defn about-page
;  [request]
;  (ring-resp/response (format "Clojure %s - served from %s"
;                              (clojure-version)
;                              (route/url-for ::about-page))))

;(defn home-page
;  [request]
;  (ring-resp/response "Hello World!!!!!!!!!!!!!!!"))


;;-----------function-----------


(def database (atom {}))
(def post-numbering (atom 1))

(defn post-list [post]
  (for [keyval (keys post)]
    [:div
     (str "Post #" (:number (keyval post)))
     [:a {:href (str "/post/" (:number (keyval post)))} [:h1 (str (:title (keyval post)))]]
     (str (:content (keyval post)))[:hr][:br][:br]]))

(defn keymaker [num]
  (cond
   (= (count (str num)) 1) (keyword (str "000" num))
   (= (count (str num)) 2) (keyword (str "00" num))
   (= (count (str num)) 3) (keyword (str "0" num))
   (= (count (str num)) 4) (keyword (str num))))


;;-----------html-----------


(defn home-html [post]
  (hc/html [:html
            [:head
             [:title "Home"]]
            [:body
             [:div {:align "center"}
              [:h1 "Home Page"]
              (if (empty? post)
                [:div "No Post Yet!" [:br][:br]]
                (post-list (into (sorted-map) post)))
              [:a {:href "/new"}
               [:button {:type "button"} "Create New Post"]]]]]))

(def new-post-html
  (hc/html [:html
            [:head
             [:title "Create New Post"]]
            [:body
             [:div {:align "center"}
              [:h1 "Create New Post"]
              [:form {:action "/ok" :method "post" :id "input-form"}
               "Title"[:br]
               [:input {:type "text" :name "title" :size "100%" :required ""}][:br][:br]
               "Content"[:br]
               [:textarea {:name "content" :size "100%" :form "input-form" :rows "20" :cols "100" :required ""}][:br][:br]
               [:a {:href "/"} [:button {:type "button"} "Cancel"]] "    "
               [:input {:type "reset" :value "Reset"}] "    "
               [:input {:type "submit" :value "Create"}]]]]]))

(defn edit-post-html [number title content]
  (hc/html [:html
            [:head
             [:title "Edit Post"]]
            [:body
             [:div {:align "center"}
              [:h1 "Edit Post"]
              [:form {:action (str "/okedit/" number) :method "post" :id "input-form"}
               "Title"[:br]
               [:input {:type "text" :name "title" :size "100%" :value (str title) :required ""}][:br][:br]
               "Content"[:br]
               [:textarea {:name "content" :size "100%" :form "input-form" :rows "20" :cols "100" :required ""} (str content)][:br][:br]
               [:a {:href "/"} [:button {:type "button"} "Cancel"]] "    "
               [:input {:type "reset" :value "Reset"}] "    "
               [:input {:type "submit" :value "Edit"}]]]]]))

(def post-ok-html
  (hc/html [:html
            [:head
             [:title "Create New Post"]]
            [:body
             [:div {:align "center"}
              [:h1 "Congratulations"]
              "Your post successfully created!"[:br][:br]
              [:a {:href "/new"} [:button {:type "button"} "Create New Post"]]"   "
              [:a {:href "/"} [:button {:type "button"} "Go to Home"]]]]]))

(def edit-ok-html
  (hc/html [:html
            [:head
             [:title "Edit Post"]]
            [:body
             [:div {:align "center"}
              [:h1 "Congratulations"]
              "Your post successfully edited!"[:br][:br]
              [:a {:href "/"} [:button {:type "button"} "Go to Home"]]]]]))

(defn post-view-html [post postid postnum]
  (hc/html [:html
            [:head
             [:title (str "Post Page :: " (:title (postid post)))]]
            [:body
             [:div {:align "center"}
              [:h1 (str (:title (postid post)))]
              (str (:content (postid post)))[:br][:br]
              [:a {:href "/"} [:button {:type "button"} "Go to Home"]]"   "
              [:a {:href (str "/delete/" postnum)} [:button {:type "button"} "Delete"]] "   "
              [:a {:href (str "/edit/" postnum)} [:button {:type "button"} "Edit"]]]]]))

(def no-page-html
  (hc/html [:html
            [:head
             [:title "Page Not Found"]]
            [:body
             [:div {:align "center"}
              [:h1 "Page Not Found"]
              "The page you requested cannot be found"]]]))

(defn delete-confirm-html [postnum]
  (hc/html [:html
            [:head
             [:title "Delete Post Confirmation"]]
            [:body
             [:div {:align "center"}
              (str "Are you sure you want to delete Post #" postnum "?")[:br][:br]
              [:form {:action (str "/deleteok/" postnum) :method "post"}
               [:a {:href (str "/post/" postnum)} [:button {:type "button"} "No"]] "   "
               [:input {:type "submit" :value "Yes"}]]]]]))

(def delete-ok-html
  (hc/html [:html
            [:head
             [:title "Delete Success"]]
            [:body
             [:div {:align "center"}
              [:h1 "Congratulations"]
              "Your post successfully deleted!"[:br][:br]
              [:a {:href "/"} [:button {:type "button"} "Go to Home"]]]]]))


;;-----------interceptor-----------


(def home-main
  (interceptor
   {:name :home-main
    :enter
    (fn [context]
      (let [request (:request context)
            response {:status 200 :body (home-html @database)}]
        (assoc context :response response)))}))

(def new-post
  (interceptor
   {:name :new-post
    :enter
    (fn [context]
      (let [request (:request context)
            response {:status 200 :body new-post-html}]
        (assoc context :response response)))}))

(def view-post
  (interceptor
   {:name :view-post
    :enter
    (fn [context]
      (let [postnum (get-in context [:request :path-params :postid])
            postid (keymaker postnum)
            response {:status 200 :body (post-view-html @database postid postnum)}]
        (if (= (postid @database) nil)
          (assoc context :response {:status 404 :body no-page-html})
          (assoc context :response response))))}))

(def create-post
  (interceptor
   {:name :create-post
    :enter
    (fn [context]
      (let [title (:title (:form-params (:request context)))
            content (:content (:form-params (:request context)))
            post-num @post-numbering
            post-num-key (keymaker post-num)]
        (swap! database assoc post-num-key {:number post-num :title title :content content})
        (swap! post-numbering inc)
        (assoc context :response {:status 200 :body post-ok-html})))}))

(def edit-post-ok
  (interceptor
   {:name :edit-post-ok
    :enter
    (fn [context]
      (let [title (:title (:form-params (:request context)))
            content (:content (:form-params (:request context)))
            post-num (get-in context [:request :path-params :postid])
            post-num-key (keymaker post-num)]
        (swap! database assoc post-num-key {:number post-num :title title :content content})
        (assoc context :response {:status 200 :body edit-ok-html})))}))

(def edit-post
  (interceptor
   {:name :edit-post
    :enter
    (fn [context]
      (let [postid (keymaker (get-in context [:request :path-params :postid]))
            title (get-in @database [postid :title])
            content (get-in @database [postid :content])
            number (get-in @database [postid :number])]
        (if (= (postid @database) nil)
          (assoc context :response {:status 404 :body no-page-html})
          (assoc context :response {:status 200 :body (edit-post-html number title content)}))))}))

(def delete-post
  (interceptor
   {:name :delete-post
    :enter
    (fn [context]
      (let [postnum (get-in context [:request :path-params :postid])
            response {:status 200 :body (delete-confirm-html postnum)}]
        (assoc context :response response)))}))

(def delete-post-ok
  (interceptor
   {:name :delete-post-ok
    :enter
    (fn [context]
      (let [postid (keymaker (get-in context [:request :path-params :postid]))
            response {:status 200 :body delete-ok-html}]
        (swap! database dissoc postid)
        (assoc context :response response)))}))

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])


;;-----------routes&server-----------


(def routes
  (route/expand-routes
   #{["/" :get (conj common-interceptors home-main)]
     ["/new" :get (conj common-interceptors new-post)]
     ["/ok" :post (conj common-interceptors create-post)]
     ["/okedit/:postid" :post (conj common-interceptors edit-post-ok)]
     ["/post/:postid" :get (conj common-interceptors view-post)]
     ["/edit/:postid" :get (conj common-interceptors edit-post)]
     ["/delete/:postid" :get (conj common-interceptors delete-post)]
     ["/deleteok/:postid" :post (conj common-interceptors delete-post-ok)]}))

;; Tabular routes
;(def routes #{["/" :get (conj common-interceptors `home-page)]
;              ["/about" :get (conj common-interceptors `about-page)]})

;; Map-based routes
;(def routes `{"/" {:interceptors [(body-params/body-params) http/html-body]
;                   :get home-page
;                   "/about" {:get about-page}}})

;; Terse/Vector-based routes
;(def routes
;  `[[["/" {:get home-page}
;      ^:interceptors [(body-params/body-params) http/html-body]
;      ["/about" {:get about-page}]]]])


;; Consumed by blog-web.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 8080
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})

