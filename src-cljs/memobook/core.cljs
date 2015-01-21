(ns memobook.core
  (:require [memobook.data :as data]
            [memobook.example-data :as example-data]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [ajax.core :as ajax]))

;; initialize data

(defn flatten-data [data]
  (->> data
       (map :content)
       (apply concat)))

(def app-state (atom {:mode :sentence}))

(defn load-item! [item]
  (swap! app-state
         (fn [a] (update-in a [(:type item)] #(conj % item)))))

(defn load-data! [data]
  (swap! app-state #(assoc %1 :word []
                              :sentence []))
  (->> data
       data/read-input
       flatten-data
       (map load-item!)
       doall))

(load-data! example-data/data)

;; some general stuff

(defmulti header-for :type)

(defmulti line-view :type)

(defmulti show-line :type)

(defmulti reset-line :type)

(defmethod reset-line :default
  [line]
  (assoc line :state nil))

(defn fake [item] {:data (repeat (count (:data item)) nil)
                   :state :fake
                   :type (:type item)})

(defn current-lines [lines]
  (->> (concat lines (repeat (fake (first lines))))
       (remove #(= :done (:state %)))
       (take 15)
       (vec)))

(defn set-seen [line]
  (when-not (:state line) (om/update! line :state :prompt)))

(defn correctness-thumb-view [state owner]
  (reify
    om/IRender
    (render [_]
      (if (= :wrong state)
        (dom/span #js {:className "glyphicon glyphicon-thumbs-down"})
        (dom/span nil nil)))))

;; display words

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
                  (if (not= (:state line) :prompt)
                    (:data line)
                    [(-> line :data first) nil nil]))))))

(defmethod show-line :word
  [line]
  (if (= :prompt (:state line))
    (assoc line :state :right)
    line))

;; display sentences

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

;; more general stuff

(defn clear-correct [lines]
  (mapv #(case (:state %)
           nil %
           :wrong (reset-line %)
           (assoc % :state :done))
        lines))

(defn reset-reviews [lines]
  (mapv reset-line lines))

(defn show-lines [lines]
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
                        (dom/button #js {:className "btn btn-default"
                                         :onClick #(om/transact! lines reset-reviews)}
                                    "reset reviews")
                        " "
                        (dom/div #js {:className "btn-group"}
                                 (dom/button #js {:onClick (fn [_]
                                                             (om/transact! lines show-lines))
                                                  :className "btn btn-default"}
                                             "show all")
                                 (dom/button #js {:className "btn btn-default"
                                                  :onClick #(om/transact! lines clear-correct)}
                                             "continue")))))))

;; TODO: proper error rather than dying for malformed edn
(defn handle-dropbox-file [response]
  (-> response
      js->clj
      first
      (get "link")
      (ajax/GET {:response-format :edn
                 :handler load-data!})))

(defn open-dropbox-chooser []
  (.choose js/Dropbox #js {:success handle-dropbox-file
                           :cancel identity
                           :linkType "direct"
                           :multiselect false
                           :extensions #js [".edn"]}))

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
                                             :onClick (fn [] (om/update! app :mode :sentence))}
                                        (dom/a nil "sentences"))
                                (dom/li #js {:role "presentation"
                                             :className (if (= :word (:mode app)) "active" "")
                                             :onClick (fn [] (om/update! app :mode :word))}
                                        (dom/a nil "words"))))
               (dom/div #js {:style #js {:fontFamily "serif"} :className "panel-body"}
                        nil
                        (om/build review-table-view ((:mode app) app)))))))

(om/root app-view app-state
  {:target (. js/document (getElementById "app"))})
