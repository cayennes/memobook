(ns memobook.data)

(defn parse-sentence [s]
  (->> s
       (re-seq #"([^＜｜＞<|>]+)|＜([^＜｜＞<|>]+)(?:｜([^＜｜＞<|>]*))(?:｜([^＜｜＞<|>]*))?＞")
       (mapv #(hash-map :text (or (nth % 1) (nth % 2))
                        :kana (nth % 3)
                        :definition (nth % 4)))))

(def parse-word identity)

(defn create-element [element-type element]
  {:type element-type
   :data (case element-type
           :sentence (parse-sentence element)
           :word (parse-word element))})

(defmulti read-input first)

(defmethod read-input 'simple [[_ element-type [grouping & elements]]]
  (if (list? (first elements))
    (->> elements
         (map #(read-input ['simple element-type %]))
         (apply concat)
         (mapv (fn [e] (update-in e [:collections 0] #(into [grouping] %)))))
    [{:content (mapv #(create-element element-type %) elements)
      :collections [[grouping]]}]))
