(ns memobook.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [memobook.vocab :refer [vocab]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(def lines
  (->> vocab
       (map :items)
       (apply concat)
       (shuffle)
       (mapv #(hash-map :data %
                        :state :unseen))))

(def app-state (atom {:lines lines
                      :lists vocab}))

(defn current-lines [app]
  (->> (:lines app)
       (#(concat % (repeat {:data [nil nil nil] :state :fake})))
       (take 17)
       (vec)))

(defn question [data] [(first data) nil nil])

(defn line-view [line owner]
  (reify
    om/IRender
    (render [_]
      (let [data (if (not= (:state line) :prompt)
                   (:data line)
                   (question (:data line)))]
        (if (= :unseen (:state line)) (om/update! line :state :prompt))
        (apply dom/tr
               #js {:onClick #(om/transact! line
                                            :state
                                            (fn [old] (cond (= old :prompt) :right
                                                            (= old :right) :wrong
                                                            (= old :wrong) :right
                                                            :else old)))}
               (dom/th nil
                       (if (= (:state line) :wrong)
                         (dom/span #js {:className "glyphicon glyphicon-thumbs-down"})))
               (map #(dom/td nil %) data))))))

(defn- show-lines [lines]
  (mapv #(if (= :prompt (:state %)) (assoc % :state :right) %) lines))

(defn- clear-correct [lines]
  (->> lines
       (filterv #(let [state (:state %)] (or (= state :unseen)
                                             (= state :wrong))))
       (mapv #(if (not= (:state %) :unseen)
                (assoc % :state :prompt)
                %))))

(defn review-table-view [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "panel panel-default"}
               (apply dom/table #js {:className "table"}
                      (apply dom/tr
                             nil
                             (dom/th nil (dom/span #js {:className "glyphicon glyphicon-thumbs-up"}))
                             (map #(dom/th nil %) ["漢字" "かな" "English"]))
                      (om/build-all line-view (current-lines app)))
               (dom/div #js {:className "panel-footer"}
                 (dom/div #js {:className "btn-group"}
                   (dom/button #js {:onClick (fn [_]
                                               (om/transact! app
                                                             :lines
                                                             show-lines))

                                    :className "btn btn-default"}
                     (dom/span #js {:className "glyphicon glyphicon-eye-open"}))
                   (dom/button #js {:className "btn btn-default"
                                    :onClick #(om/transact! app
                                                            :lines
                                                            clear-correct)}
                     (dom/span #js {:className "glyphicon glyphicon-play"}))))))))

(om/root review-table-view app-state
  {:target (. js/document (getElementById "app"))})
