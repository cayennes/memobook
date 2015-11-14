(ns memobook.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [memobook.data :as data]
            [memobook.dropbox :as dropbox]
            [memobook.example-data :as example-data]
            [memobook.srs :as srs]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [cljs.core.async :refer [chan put! take! <! >!]]))

;; # Data loading

(defn load-item
  [current item]
  (update-in current [(:type item)] #(conj % item)))

(defn flatten-data
  "transform data from memo form to a flat list of lines to study"
  [data]
  (->> data
       (map :content)
       (apply concat)))

(defn load-data
  [data]
  (let [sort-fn (if (dropbox/dropbox-authenticated?)
                  srs/sort-review-queue
                  shuffle)]
    (-> data
        data/read-input
        flatten-data
        (#(reduce load-item {:sentence [] :word []} %))
        (update-in [:word] sort-fn)
        (update-in [:sentence] sort-fn))))

;; The data will be read in; set the mode of the app to show the sentences
(defonce app-state (atom {:mode :sentence
                          :view-settings {:per-page 15}}))

;; putting the app on this channel will cause data to be updated
;; loop that handles it defined in this defonce; we don't want to be loading
;; data multiple times at once
(defonce data-update-ch
  (let [ch (chan 1)] ;; queue only one update request while updating
    (go
      (loop []
        (let [app (<! ch)
              data (if (:logged-in @app)
                     (<! (dropbox/fetch-data-from-dropbox))
                     example-data/data)]
          (om/transact! app #(merge % (load-data data))))
        (recur)))
    ch))

;; # Logging in and out

(defn log-in-or-out
  "log in or out of dropbox and update the app and data as appropriate"
  [action app & {:keys [interactive] :or {interactive true}}]
  (go
    (let [previously-logged-in? (:logged-in @app)
          logged-in? (<! (case action
                           :login (dropbox/dropbox-login :interactive interactive)
                           :logout (dropbox/dropbox-logout)))]
      (when-not (= logged-in? previously-logged-in?)
        (om/update! app :logged-in logged-in?)
        (put! data-update-ch app)))))

(def login
  "log in to dropbox

  interactive unless passed :interactive false"
  (partial log-in-or-out :login))

(def logout
  "log out of dropbox and update data"
  (partial log-in-or-out :logout))

;; # Views for types of things to review

(defmulti header-for
  "a list of titles appropriate for an html table of this type of item"
  :type)

(defmulti line-view
  "om component to for viewing the item as a line of a table"
  :type)

(defmulti show-line
  "a version of the line completely showing the answer"
  :type)

(defmulti reset-line
  "a version of the line in its initial state"
  :type)

(defmethod reset-line :default
  [line]
  (assoc line :state nil))

(defn fake
  "a placeholder blank item that can fill the same role as the one given"
  [item]
  {:data (repeat (count (:data item)) nil)
   :state :fake
   :type (:type item)})

(defn current-lines
  "just the lines that should be shown of the ones given"
  [line-count lines]
  (->> (concat lines (repeat (fake (first lines))))
       (remove #(= :done (:state %)))
       (take line-count)
       vec))

(defn set-seen
  "a copy of the item with state indicating that it has been seen"
  [line]
  (when-not (:state line) (om/update! line :state :prompt)))

(defn correctness-thumb-view [state owner]
  "om component showing a thumbs down if the item is marked as wrong"
  (reify
    om/IRender
    (render [_]
      (html [:span (when (= :wrong state)
                     {:class "glyphicon glyphicon-thumbs-down"})]))))

;; ## Words

(defmethod header-for :word [_]
  ["漢字" "かな" "English"])

(defmethod line-view :word
  [line owner]
  (reify
    om/IRender
    (render [_]
      (set-seen line)
      (print line)
      (html
        [:tr {:onClick (fn [] (om/transact! line
                                            :state
                                            #(condp = %
                                               :prompt :right
                                               :right :wrong
                                               :wrong :right
                                               %)))}
         [:th (om/build correctness-thumb-view (:state line))]
         (map #(vector :td %)
              (if (not= (:state line :prompt) :prompt)
                (:data line)
                [(-> line :data first) nil nil]))]))))

(defmethod show-line :word
  [line]
  (if (= :prompt (:state line))
    (assoc line :state :right)
    line))

;; ## Sentences

(defmethod header-for :sentence [_]
  ["文"])

(defn sentence-element-view [element owner]
  (reify
    om/IRender
    (render [_]

      (html
        [:ruby {:onClick (fn []
                           (when (or (:show-kana @element) (= "" (:kana @element)))
                             (om/transact! element #(assoc % :show-translation true)))
                           (om/transact! element #(assoc % :show-kana true)))}
         (:text element)
         (when (:show-kana element) [:rt (:kana element)])
         (when (:show-translation element)
           ;; rubyPosition "under" isn't supported by browsers/sablono.
           ;; oh well.
           [:rt #_{:style {:rubyPosition "under"}} (:definition element)])]))))

(defn sentence-view [sentence owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:span {:style {:fontSize "16pt"}}
         (om/build-all sentence-element-view (:data sentence))]))))

(defmethod line-view :sentence
  [line owner]
  (reify
    om/IRender
    (render [_]
      (set-seen line)
      (html
        [:tr
         [:th {:onClick (fn [] (om/transact! line
                                             :state
                                             #(if (= :wrong %)
                                                :right
                                                :wrong)))}
          (om/build correctness-thumb-view (:state line))]
         [:td (om/build sentence-view line)]]))))

(defmethod show-line :sentence [line]
  (if (:state line)
    (assoc line
           :data (mapv #(assoc % :show-kana true :show-translation true)
                       (:data line)))
    line))

(defmethod reset-line :sentence [line]
  (-> line
      (update-in [:data] (partial mapv #(assoc % :show-kana false
                                                 :show-translation false)))
      (assoc :state nil)))

;; # Main views

(defn clear-correct
  "set correct things to done and incorrect things to their initial state

  update srs information"
  [lines]
  (mapv #(case (:state %)
           nil %
           :done %
           :wrong (do (when (dropbox/dropbox-authenticated?)
                        (srs/update-srs! % :wrong))
                      (reset-line %))
           (do (when (dropbox/dropbox-authenticated?)
                 (srs/update-srs! % :right))
               (assoc % :state :done)))
        lines))

(defn show-lines
  "set everything to shown"
  [lines]
  (mapv show-line lines))

(defn stats-view [items]
  (reify
    om/IRender
    (render [_]
      (html
        [:p
         "total studying: " (count items) ". "
         "new available: " (count (remove :srs items)) ". "]))))

(defn review-table-view [{:keys [lines view-settings]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        (if (pos? (count lines))
          [:div.panel.panel-default
           [:table.table
            [:thead
             [:tr
              [:th [:span.glyphicon.glyphicon-thumbs-up]]
              (map #(vector :th %) (header-for (first lines)))]]
            [:tbody
             ;; TODO: this stopped working when I upgraded om from
             ;; 0.7.3 to 0.8.8 and I can't figure out why
             #_(om/build-all line-view (current-lines lines))
             (map #(om/build line-view %)
                  (current-lines (:per-page view-settings)
                                 lines))]]
           [:div.panel-footer
            [:div.btn-group
             [:button.btn.btn-default {:onClick #(om/transact! lines show-lines)}
              "show all"]
             [:button.btn.btn-default {:onClick #(om/transact! lines clear-correct)}
              "continue"]]]]
          [:div])))))

(defn per-page-button [{:keys [settings button-value]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:button {:className (if (= (:per-page settings) button-value)
                               "active btn btn-default"
                               "btn btn-default")
                  :onClick #(om/update! settings [:per-page] button-value)}
         button-value]))))

(defn view-settings-view [settings owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div.panel-footer
         "reviews per page: "
         [:div.btn-group
          (om/build-all per-page-button
                        (map #(array-map :settings settings :button-value %)
                             (range 5 21 5)))]]))))

(defn app-view [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      ;; unobviously, this will also always update the data on first load,
      ;; because login refreshes the data when the login state changes; it
      ;; starts out as nil before attempting any login, and is either true or
      ;; false afterward
      (login app :interactive false))

    om/IRender
    (render [_]
      (html
        [:div.panel.panel-default
         [:div.panel-heading
          (if (:logged-in app)
            [:button.btn.btn-default {:onClick #(logout app)}
             "log out of Dropbox"]
            [:button.btn.btn-default {:onClick #(login app)}
             "log in to Dropbox"])
          " "
          [:button.btn.btn-default {:onClick #(put! data-update-ch app)}
           "reload data"]]
         [:nav.panel-body
          [:ul.nav.nav-tabs
           (when (seq (:sentence app))
             [:li {:role "presentation"
                   :className (if (= :sentence (:mode app)) "active" "")
                   :onClick #(om/update! app :mode :sentence)}
              [:a "sentences"]])
           (when (seq (:word app))
             [:li {:role "presentation"
                   :className (if (= :word (:mode app)) "active" "")
                   :onClick #(om/update! app :mode :word)}
              [:a "words"]])]]
         [:div.panel-body {:style {:fontFamily "serif"}}
          (om/build review-table-view {:lines ((:mode app) app)
                                       :view-settings (:view-settings app)})
          (om/build stats-view ((:mode app) app))
          (om/build view-settings-view (:view-settings app))]]))))

(om/root app-view app-state
  {:target (. js/document (getElementById "app"))})
