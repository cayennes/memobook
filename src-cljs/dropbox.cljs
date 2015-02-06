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

(defn dropbox-authenticated? []
  (and @db-client (.isAuthenticated @db-client)))

(defn dropbox-login
  "Attempt to log into dropbox (and do setup)

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
  "log out of dropbox

  returns a channel which will recieve false (indicating the login state has
  become false) when the logout is complete"
  []
  (let [ch (chan)]
    (if (dropbox-authenticated?)
      (.signOut @db-client #(put! ch false))
      (put! ch false))
    ch))

(defn srs-table []
  (when-not @table
    (reset! table (.getTable @datastore "srs")))
  @table)

(defn insert-item! [item item-key]
  (let [info (assoc (:srs item) :key item-key)]
    (.insert (srs-table) (clj->js info))))

(defn retrieve-item [item-key]
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

(defn fetch-data-from-dropbox
  "Fetches the data stored in users' memobook.edn file

  returns a channel which will receive the data"
  []
  (let [ch (chan)
        callback (fn [err content]
                   (when err (print err))
                   (->> content
                        reader/read-string
                        (put! ch)))]
    (.readFile @db-client "memobook.edn" callback)
    ch))
