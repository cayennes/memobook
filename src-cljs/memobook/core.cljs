(ns memobook.core
  (:require [memobook.data :as data]
            [memobook.example-data :as example-data]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [ajax.core :as ajax]))

;; # Data loading

(defn flatten-data
  "transform data from memo form to a flat list of lines to study"
  [data]
  (->> data
       (map :content)
       (apply concat)))

;; The data will be read in; set the mode of the app to show the sentences
(def app-state (atom {:mode :sentence}))

(defn load-item!
  "add given item to the list of items of its type in the app state"
  [item]
  (swap! app-state
         (fn [a] (update-in a [(:type item)] #(conj % item)))))

(defn load-data!
  "set the app state to the given data"
  [data]
  ;; clear existing data
  (swap! app-state #(assoc %1 :word []
                              :sentence []))
  ;; read the new data in
  (->> data
       data/read-input
       flatten-data
       (map load-item!)
       doall))

;; Load the example data to start with
(load-data! example-data/data)

;; ## Dropbox data loading
;;
;; memobook can load data to review from an edn file stored in dropbox

;; TODO: proper error rather than dying for malformed edn
(defn handle-dropbox-file
  "load the data in the edn file indicated by the dropbox callback"
  [response]
  (-> response
      js->clj
      first
      (get "link")
      (ajax/GET {:response-format :edn
                 :handler load-data!})))

(defn open-dropbox-chooser
  "use dropbox's chooser functionality to select an edn file"
  []
  (.choose js/Dropbox #js {:success handle-dropbox-file
                           :cancel identity
                           :linkType "direct"
                           :multiselect false
                           :extensions #js [".edn"]}))

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
  [lines]
  (->> (concat lines (repeat (fake (first lines))))
       (remove #(= :done (:state %)))
       (take 15)
       (vec)))

(defn set-seen
  "a copy of the item with state indicating that it has been seen"
  [line]
  (when-not (:state line) (om/update! line :state :prompt)))

(defn correctness-thumb-view [state owner]
  "om component showing a thumbs down if the item is marked as wrong"
  (reify
    om/IRender
    (render [_]
      (if (= :wrong state)
        (dom/span #js {:className "glyphicon glyphicon-thumbs-down"})
        (dom/span nil nil)))))

;; ## Words

(defmethod header-for :word [_]
  ["漢字" "かな" "English"])

(defmethod line-view :word
  [line owner]
  (reify
    om/IRender
    (render [_]
      (set-seen line)
      (apply dom/tr
             #js {:onClick (fn [] (om/transact! line
                                                :state
                                                #(condp = %
                                                   :prompt :right
                                                   :right :wrong
                                                   :wrong :right
                                                   %)))}
             (dom/th nil (om/build correctness-thumb-view (:state line)))
             (map #(dom/td nil %)
                  (if (not= (:state line :prompt) :prompt)
                    (:data line)
                    [(-> line :data first) nil nil]))))))

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
      (dom/ruby #js {:onClick (fn []
                                (when (or (:show-kana @element) (= "" (:kana @element)))
                                  (om/transact! element #(assoc % :show-translation true)))
                                (om/transact! element #(assoc % :show-kana true)))}
                (:text element)
                (when (:show-kana element) (dom/rt nil (:kana element)))
                (when (:show-translation element)
                  ;; rubyPosition "under" isn't supported by browsers.  oh well.
                  (dom/rt #js {:style #js {:rubyPosition "under"}}
                          (:definition element)))))))

(defn sentence-view [sentence owner]
  (reify
    om/IRender
    (render [_]
      (apply dom/span
             #js {:style #js {:fontSize "16pt"}}
             (om/build-all sentence-element-view (:data sentence))))))

(defmethod line-view :sentence
  [line owner]
  (reify
    om/IRender
    (render [_]
      (set-seen line)
      (dom/tr
        nil
        (dom/th #js {:onClick (fn [] (om/transact! line
                                                   :state
                                                   #(if (= :wrong %)
                                                      :right
                                                      :wrong)))}
                (om/build correctness-thumb-view (:state line)))
        (dom/td nil (om/build sentence-view line))))))

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
  "set correct things to done and incorrect things to their initial state"
  [lines]
  (mapv #(case (:state %)
           nil %
           :wrong (reset-line %)
           (assoc % :state :done))
        lines))

(defn reset-reviews
  "reset everything to the initial state"
  [lines]
  (mapv reset-line lines))

(defn show-lines
  "set everything to shown"
  [lines]
  (mapv show-line lines))

(defn review-table-view [lines owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "panel panel-default"}
               (apply dom/table
                      #js {:className "table"}
                      (apply dom/tr
                             nil
                             (dom/th nil (dom/span #js {:className "glyphicon glyphicon-thumbs-up"}))
                             (map #(dom/th nil %) (header-for (first lines))))
                      (om/build-all line-view (current-lines lines)))
               (dom/div #js {:className "panel-footer"}
                        (dom/button #js {:onClick #(om/transact! lines reset-reviews)
                                         :className "btn btn-default"}
                                    "reset reviews")
                        " "
                        (dom/div #js {:className "btn-group"}
                                 (dom/button #js {:onClick #(om/transact! lines show-lines)
                                                  :className "btn btn-default"}
                                             "show all")
                                 (dom/button #js {:className "btn btn-default"
                                                  :onClick #(om/transact! lines clear-correct)}
                                             "continue")))))))

(defn app-view [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "panel panel-default"}
               (dom/div #js {:className "panel-heading"}
                        (dom/button #js {:onClick open-dropbox-chooser
                                         :className "btn btn-default"}
                                    "load edn file from DropBox")
                        " "
                        (dom/button #js {:onClick #(load-data! example-data/data)
                                         :className "btn btn-default"}
                                    "reload example data"))
               (dom/nav #js {:className "panel-body"}
                        (dom/ul #js {:className "nav nav-tabs"}
                                (dom/li #js {:role "presentation"
                                             :className (if (= :sentence (:mode app)) "active" "")
                                             :onClick #(om/update! app :mode :sentence)}
                                        (dom/a nil "sentences"))
                                (dom/li #js {:role "presentation"
                                             :className (if (= :word (:mode app)) "active" "")
                                             :onClick #(om/update! app :mode :word)}
                                        (dom/a nil "words"))))
               (dom/div #js {:style #js {:fontFamily "serif"} :className "panel-body"}
                        nil
                        (om/build review-table-view ((:mode app) app)))))))

(om/root app-view app-state
  {:target (. js/document (getElementById "app"))})
