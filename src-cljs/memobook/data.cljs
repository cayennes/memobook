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

(defn read-simple-grouping [element-type [grouping & elements]]
  (if (list? (first elements))
    (->> (map #(read-simple-grouping element-type %) elements)
         (apply concat)
         (map (fn [e] (update-in e [:collections 0] #(into [grouping] %)))))
    [{:content (mapv #(create-element element-type %) elements)
      :collections [[grouping]]}]))

(defmethod read-input 'simple [[_ element-type & groups]]
  (->> groups
       (map #(read-simple-grouping element-type %))
       (apply concat)))

(defmethod read-input 'composite [[_ & contents]]
  (->> contents
       (map read-input)
       (apply concat)))
