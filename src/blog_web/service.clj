(ns blog-web.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [io.pedestal.test :as test]
            [io.pedestal.interceptor :refer [interceptor]]
            [hiccup.core :as hc]))


;;-----------function-----------


(def database (atom {}))
(def post-numbering (atom 1))

(defn post-list [post]
  (for [keyval (keys post)]
    [:div {:class "col-sm-4"}
     [:h5 [:small (str "Post #" (:number (keyval post)))]]
     [:a {:href (str "/post/" (:number (keyval post)))} [:h3 (str (:title (keyval post)))]]
     (str (:content (keyval post)))[:br][:br]]))

(defn keymaker [num]
  (cond
   (= (count (str num)) 1) (keyword (str "000" num))
   (= (count (str num)) 2) (keyword (str "00" num))
   (= (count (str num)) 3) (keyword (str "0" num))
   (= (count (str num)) 4) (keyword (str num))))


(defn bootstrap []
  (for [cnt (range 4)]
    ([[:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"}]
      [:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"}]
      [:script {:src "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"}]] cnt)))


(defn navpanel [active]
  [:nav {:class "navbar navbar-inverse"}
              [:div {:class "container-fluid"}
               [:div {:class "navbar-header"}
                [:a {:class "navbar-brand" :href "/"} "BlogWeb"]]
               [:ul {:class "nav navbar-nav"}
                (if (= active 1)
                  [:li {:class "active"} [:a {:href "/"} "Home"]]
                  [:li [:a {:href "/"} "Home"]])
                [:li [:a {:href "/"} "About"]]]
               [:ul {:class "nav navbar-nav navbar-right"}
                [:li [:a {:href "#"} [:span {:class "glyphicon glyphicon-user"}] " Sign Up"]]
                [:li [:a {:href "#"} [:span {:class "glyphicon glyphicon-log-in"}] " Login"]]]]])


;;-----------html-----------


(defn home-html [post]
  (hc/html [:html
            [:head
             [:title "Home"]
             (bootstrap)]
            [:body
             (navpanel 1)
             [:div {:class "jumbotron text-center"}
              [:h1 "Home Page"]
              [:p "Welcome to blog test page"]]
             [:div {:class "container"}
              (if (empty? post)
                [:div {:class "container-fluid"}
                 [:div {:class "alert alert-info"}
                  [:strong "You have no post yet!"] " Click the new post button to create a new post"]]
                [:div {:class "row"}
                 (post-list (into (sorted-map) post))])
              [:div {:class "text-center"}
               [:br][:br]
               [:a {:href "/new"}
                [:button {:class "btn btn-primary" :type "button"} "New Post"]]
               [:br][:br]]]]]))

(def new-post-html
  (hc/html [:html
            [:head
             [:title "Create New Post"]
             (bootstrap)]
            [:body
             (navpanel 0)
             [:div {:align "center"}
              [:h1 "Create New Post"]]
             [:div {:class "container"}
              [:form {:action "/ok" :method "post" :id "input-form"}
               [:div {:class "form-group"}
                [:label {:for "title"} "Title"]
                [:input {:type "text" :class "form-control" :id "title" :name "title" :required ""}]]
               [:div {:class "form-group"}
                [:label {:for "content"} "Content"]
                [:textarea {:class "form-control" :rows "20" :id "content" :name "content" :required ""}]]
               [:div {:class "text-center"}
                [:div {:class "btn-group"}
                 [:a {:href "/" :class "btn btn-primary"} "Cancel"]
                 [:button {:type "reset" :class "btn btn-primary"} "Reset"]
                 [:button {:type "submit" :class "btn btn-primary"} "Submit"]]]]]]]))

(defn edit-post-html [number title content]
  (hc/html [:html
            [:head
             [:title "Edit Post"]
             (bootstrap)]
            [:body
             (navpanel 0)
             [:div {:align "center"}
              [:h1 "Edit Post"]]
             [:div {:class "container"}
              [:form {:action (str "/okedit/" number) :method "post" :id "input-form"}
               [:div {:class "form-group"}
                [:label {:for "title"} "Title"]
                [:input {:type "text" :class "form-control" :id "title" :name "title" :value (str title)}]]
               [:div {:class "form-group"}
                [:label {:for "content"} "Content"]
                [:textarea {:class "form-control" :rows "20" :id "content" :name "content"} (str content)]]
               [:div {:class "text-center"}
                [:div {:class "btn-group"}
                 [:a {:href (str "/post/" number) :class "btn btn-primary"} "Cancel"]
                 [:button {:type "reset" :class "btn btn-primary"} "Reset"]
                 [:button {:type "submit" :class "btn btn-primary"} "Edit"]]]]]]]))

(def post-ok-html
  (hc/html [:html
            [:head
             [:title "Create New Post"]
             (bootstrap)]
            [:body
             (navpanel 0)
             [:div {:class "container-fluid"}
              [:div {:class "alert alert-success"}
               [:strong "Congratulations!"] " Your post successfully created!"]
              [:div {:class "text-center"}
               [:div {:class "btn-group"}
                [:a {:href "/new" :class "btn btn-primary"} "New Post"]
                [:a {:href "/" :class "btn btn-primary"} "Home"]]]]]]))

(def edit-ok-html
  (hc/html [:html
            [:head
             [:title "Edit Post"]
             (bootstrap)]
            [:body
             (navpanel 0)
             [:div {:class "container-fluid"}
              [:div {:class "alert alert-success"}
               [:strong "Congratulations!"] " Your post successfully edited!"]
              [:div {:class "text-center"}
               [:a {:href "/" :class "btn btn-primary"} "Home"]]]]]))


(defn post-view-html [post postid postnum]
  (hc/html [:html
            [:head
             [:title (str "Post Page :: " (:title (postid post)))]
             (bootstrap)]
            [:body
             (navpanel 0)
             [:div {:class "container"}
              [:h1 [:strong (str (:title (postid post)))]]
              [:br]
              (str (:content (postid post)))
              [:br][:br]
              [:div {:class "text-center"}
               [:div {:class "btn-group"}
                [:a {:href "/" :class "btn btn-primary"} "Home"]
                [:a {:href (str "/delete/" postnum) :class "btn btn-primary"} "Delete"]
                [:a {:href (str "/edit/" postnum) :class "btn btn-primary"} "Edit"]]]]]]))


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
  #{["/" :get (conj common-interceptors home-main)]
    ["/new" :get (conj common-interceptors new-post)]
    ["/ok" :post (conj common-interceptors create-post)]
    ["/okedit/:postid" :post (conj common-interceptors edit-post-ok)]
    ["/post/:postid" :get (conj common-interceptors view-post)]
    ["/edit/:postid" :get (conj common-interceptors edit-post)]
    ["/delete/:postid" :get (conj common-interceptors delete-post)]
    ["/deleteok/:postid" :post (conj common-interceptors delete-post-ok)]})

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

