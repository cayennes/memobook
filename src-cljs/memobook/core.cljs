(ns memobook.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [memobook.vocab :refer [vocab]]
            [memobook.sentences :refer [sentences]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

;; initialize data

(def word-lines (shuffle vocab))

(def app-state (atom {:words word-lines
                      :sentences sentences
                      :mode :sentences}))

;; some general stuff

(defmulti header-for :type)

(defmulti line-view :type)

(defn fake [item] {:data (repeat (count item) nil)
                   :state :fake
                   :type (:type item)})

(defn current-lines [lines]
  (->> lines
       (#(concat % (repeat (fake (first %)))))
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

(defn show-lines [lines]; TODO: for sentences as well
  (mapv #(if (= :prompt (:state %)) (assoc % :state :right) %) lines))

;; display sentences

(defmethod header-for :sentence [_]
  ["文"])

(defn sentence-element-view [element owner]
  (reify
    om/IRender
    (render [_]
      (dom/ruby #js {:onClick (fn [] (if-not (:show-kana @element)
                                       (om/transact! element #(assoc % :show-kana true))
                                       (om/transact! element #(assoc % :show-translation true))))}
                (:text element)
                  (when (:show-kana element) (dom/rt nil (:kana element)))
                  (when (:show-translation element)
                    ;; ruby-position "under" isn't supported by browsers.  oh well.
                    (dom/rt #js {:style #js {:ruby-position "under"}}
                            (:definition element)))))))

(defn sentence-view [sentence owner]
  (reify
    om/IRender
    (render [_]
      (apply dom/span
             #js {:style #js {:font-size "16pt"}}
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

;; more general stuff

(defn reset-line [line]
  (if-let [prompt-line (when (:state line) (assoc line :state :prompt))]
    (if (= :sentence (:type prompt-line))
      (update-in prompt-line
                 [:data]
                 (partial mapv #(assoc % :show-kana false :show-definition false)))
      prompt-line)
    line))

(defn clear-correct [lines]; TODO: make this not break when going to none
  (->> lines
       (filterv #(= :wrong (:state % :wrong)))
       (mapv reset-line)))

(defn review-table-view [lines owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "panel panel-default"}
               (apply dom/table #js {:className "table"}
                      (apply dom/tr
                             nil
                             (dom/th nil (dom/span #js {:className "glyphicon glyphicon-thumbs-up"}))
                             (map #(dom/th nil %) (header-for (first lines))))
                      (om/build-all line-view (current-lines lines)))
               (dom/div #js {:className "panel-footer"}
                 (dom/div #js {:className "btn-group"}
                   (dom/button #js {:onClick (fn [_]
                                               (om/transact! lines show-lines))
                                    :className "btn btn-default"}
                     (dom/span #js {:className "glyphicon glyphicon-eye-open"}))
                   (dom/button #js {:className "btn btn-default"
                                    :onClick #(om/transact! lines clear-correct)}
                     (dom/span #js {:className "glyphicon glyphicon-play"}))))))))

(defn app-view [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "panel panel-default"}
               (dom/nav #js {:className "panel-body"}
                        (dom/ul #js {:className "nav nav-tabs"}
                                (dom/li #js {:role "presentation"
                                             :className (if (= :sentences (:mode app)) "active" "")
                                             :onClick (fn [] (om/update! app :mode :sentences))}
                                        (dom/a nil "sentences"))
                                (dom/li #js {:role "presentation"
                                             :className (if (= :words (:mode app)) "active" "")
                                             :onClick (fn [] (om/update! app :mode :words))}
                                        (dom/a nil "words"))))
               (dom/div #js {:style #js {:font-family "serif"} :className "panel-body"}
                        nil
                        (om/build review-table-view ((:mode app) app)))))))

(om/root app-view app-state
  {:target (. js/document (getElementById "app"))})
