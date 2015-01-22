(ns memobook.data)

;; Data is read in from edn files that are structured to be easy to write
;;
;; It is output as a list of memos, which are groupings of elements to study.
;;
;; Memos have a list of collections, which are like tags except that they're
;; lists so that they can represent a hierarchy, for example chapters within
;; books.  These can be used to indicate the sources of things or also
;; groupings of things to study.  Collections are not currently used for
;; anything.

;; # Elements
;;
;; Elements are individual things to study; the lines in the review tables
;;
;; Here are functions to read them in from the data in the edn file to data
;; format used by the app.

(defn parse-sentence
  "parse a sentence

  Words that have a reading and/or definition should be written in one of these
  forms:

  * ＜word｜reading＞
  * ＜word｜reading｜definition＞
  * ＜word｜｜definition＞

  Note that ＜, ｜, and ＞ are the full-width Japanese characters (this is more
  pleasant to type because it is less switching between input modes.)

  Currently does not support including those literally in the sentence, nor the
  ascii equivalents <, |, or > (the latter to avoid hard-to-find typos; I chose
  not to use these interchangeably in order to increase the searchability of
  the text.)"
  [s]
  (->> s
       (re-seq #"([^＜｜＞<|>]+)|＜([^＜｜＞<|>]+)(?:｜([^＜｜＞<|>]*))(?:｜([^＜｜＞<|>]*))?＞")
       (mapv #(hash-map :text (or (nth % 1) (nth % 2))
                        :kana (nth % 3)
                        :definition (nth % 4)))))

(def parse-word
  "words take the form [word reading definition] in both input and output"
  identity)

(defn create-element [element-type element]
  "create an element of the given type and raw input element"
  {:type element-type
   :data (case element-type
           :sentence (parse-sentence element)
           :word (parse-word element))})

;; # Data format

(defmulti read-input
  "read an input list whose first element is the type of reader to use"
  first)

;; ## Simple data
;;
;; This organizes data file based on a heirarchical source groupings which then
;; get turned into collection lists in the output.  The element type is
;; indicated by a string after the symbol simple; multiple types of elements
;; cannot be mixed within a simple list
;;
;;     (simple "type-of-data"
;;       ("book title"
;;         ("book chapter"
;;           ["some" "data"]
;;           ["other" "data"])
;;         ("another chapter"
;;           ["more" "data"])))

(defn read-simple-grouping
  "recursive function to read in a list inside a simple grouping"
  [element-type [grouping & elements]]
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

;; ## Composite data
;;
;; Combine multiple inputs; since simple is currently the only other format the
;; main purpose of this is in order to read in lists of different element types

(defmethod read-input 'composite [[_ & contents]]
  (->> contents
       (map read-input)
       (apply concat)))
