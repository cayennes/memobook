(ns memobook.dropbox
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.reader :as reader]
            [cljs.core.async :refer [chan put! take! <! >!]]))

(enable-console-print!);; TODO: actually do something with errors, remove this

(def api-key "cjlzkx0h9nv6gq8")

(defonce db-client (atom))
(defonce datastore-manager (atom))
(defonce datastore (atom))
(defonce table (atom))

;; # Login and logout

(defn dropbox-authenticated? []
  (and @db-client (.isAuthenticated @db-client)))

(defn dropbox-login
  "Attempt to log into dropbox, and do setup

  Returns a channel which will deliver a boolean indicating the result"
  [& {:keys [interactive]}]
  (let [ch (chan)]
    (go
      (when-not @db-client
        (reset! db-client (js/Dropbox.Client. #js {"key" api-key})))

      (.authenticate @db-client #js {"interactive" false})
      (when (and interactive (not (dropbox-authenticated?)))
        (.authenticate @db-client))

      (if (dropbox-authenticated?)
        ;; datastore initialized here because it happens in a callback, it's
        ;; not worth making everything be callbacks/channels, and login is the
        ;; time when it makes sense to be waiting on that kind of thing
        (do
          (when-not @datastore-manager
            (reset! datastore-manager (.getDatastoreManager @db-client)))

          (if @datastore
            (put! ch true)
            (.openDefaultDatastore @datastore-manager
                                   (fn [err ds]
                                     (when err (print err))
                                     (reset! datastore ds)
                                     (put! ch true)))))
        (put! ch false)))
    ch))

(defn dropbox-logout
  "Log out of dropbox

  Returns a channel which it will deliver false (indicating the login state has
  become false) to when the logout is complete"
  []
  (let [ch (chan)]
    (if (dropbox-authenticated?)
      (.signOut @db-client #(put! ch false))
      (put! ch false))
    ch))

;; # Storage and retrieval of review scheduling info

(defn srs-table
  "Returns the dropbox datastore table with scheduling data"
  []
  (when-not @table
    (reset! table (.getTable @datastore "srs")))
  @table)

(defn insert-item!
  "Saves srs info for the given item with srs info under the given key"
  [item item-key]
  (let [info (assoc (:srs item) :key item-key)]
    (.insert (srs-table) (clj->js info))))

(defn retrieve-item
  "Returns srs info for the item with the given key"
  [item-key]
  (when-let [found (some->> item-key
                            (array-map :key)
                            clj->js
                            (.query (srs-table))
                            js->clj
                            seq
                            (reduce #(if (> (.get %1 "reviewed")
                                            (.get %2 "reviewed"))
                                       %1
                                       %2)))]
    {:reviewed (.get found "reviewed")
     :interval (.get found "interval")}))

;; # Loading of study content from edn file

(defn fetch-data-from-dropbox
  "Fetches the data to study stored in users' memobook.edn file

  returns a channel which will receive the data"
  []
  (let [ch (chan)]
    (.readFile @db-client
               "memobook.edn" 
               (fn [err content]
                 (when err (print err))
                 (put! ch (reader/read-string content))))
    ch))
