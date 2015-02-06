(ns memobook.srs
  (:require [memobook.dropbox :as dropbox]
            [clojure.string :as string]))

;; # Date/time functions
;;
;; Working with js/Date milliseconds since what I'm trying to do with intervals
;; and such isn't that complicated but also isn't supported by existing
;; libraries

(defn now []
  "current time"
  ;; Avoid type warnings by using milliseconds everywhere
  (.getTime (js/Date.)))

(defn elapsed-interval [previous]
  "interval between the given time and now"
  (- (now) previous))

(def min-interval
  "four hours in milliseconds

  review intervals start out as and can't get shorter than this"
  (* 4; four hours
     60; in minutes
     60; in seconds
     1000)); in milliseconds

;; # SRS algorithms
;;
;; The basic spaced repitition algorithms used by memobook are quite simple.  I
;; don't think making them complicated makes sense.  My experience with hugely
;; overdue anki cards is that I still get a bunch of them right anyway, so my
;; intuation is that there's too much that goes into the forgetting rate of
;; something for complicated systems using the information available to
;; software to be able to increase the efficiency all that much.
;;
;; I also don't like the chore aspect of most SRS systems.  Here due date is
;; completely hidden from the UI but used internally to make a best effort at
;; showing the most worthwhile things to review.

;; In general the items here expect to receive a merged map from the
;; generally-unchanging item info stored in the edn and the SRS state info
;; stored in the datastore; this seems like the approach most independant of
;; those lower level implementation details.

(defn due-factor [item]
  "a number indicating degree of over/under-dueness of the item

  item is of the same form expected by sort-review-queue"
  (let [interval (-> item :srs :interval)
        since-review (-> item :srs :reviewed elapsed-interval)]
    (* -1; to make it a descending sort.
       (+ 1 (rand 0.1));; randomization factor to decrease clumping
       (if interval
         (/ since-review interval)
         0.9))))

(defn new-interval
  [old-interval actual-interval result]
  (if old-interval
    (case result
      :right (max (+ old-interval actual-interval)
                  (* 2.5 actual-interval)
                  min-interval)
      :wrong min-interval)
    min-interval))

(defn updated-item
  "item updated with new interval based on the result and current srs state

  result should be :right or :wrong

  item is a review item with the addition of an :srs key whose value is a map
  containing :reviewed and :interval"
  [item result]
  (let [old-interval (-> item :srs :interval)
        actual-interval (-> item :srs :reviewed elapsed-interval)
        interval (new-interval old-interval actual-interval result)]
    (assoc item :srs {:reviewed (now) :interval interval})))

;; # Utilities for working with item data

(defmulti normalized
  "reduce item to the essential question it represents

  unique identifier not too likely to change with insignificant edits

  this is used to find the item's SRS data in the datastore"
  :type)

(defmethod normalized :word [item]
  (-> item :data first))

(defmethod normalized :sentence [item]
  (->> item :data (map :text) string/join))

(defn item-key [item]
  (str (:type item) " " (normalized item)))

;; # Working with the data in the datastore

(defn item-with-srs [item]
  (->> item
       item-key
       dropbox/retrieve-item
       (assoc item :srs)))

(defn sort-review-queue [items]
  (->> items
       (map item-with-srs)
       (sort-by due-factor)
       (into (empty items))))

(defn update-srs! [item result]
  (-> item
      item-with-srs
      (updated-item result)
      (dropbox/insert-item! (item-key item))))
