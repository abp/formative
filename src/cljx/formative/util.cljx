(ns formative.util
  (:require [clojure.string :as string]
            [clj-time.core :as ct]
            [clj-time.coerce :as cc]
            [clj-time.format :as cf])
  (:import org.joda.time.DateTime
           org.joda.time.LocalDate
           org.joda.time.LocalTime))

(defn normalize-options [opts]
  (let [opts (cond
               (fn? opts) (opts)
               (delay? opts) @opts
               :else opts)]
    (if (coll? (first opts))
      (if (map? (first opts))
        (map (juxt :value :label) opts)
        opts)
      (map #(vector % %) opts))))

(defn parse-date [s & [format]]
  (cf/parse (cf/formatter (or format "yyyy-MM-dd"))
            s))

(defn to-timezone [d timezone]
  (if timezone
    (let [timezone (if (string? timezone)
                      (ct/time-zone-for-id timezone)
                      timezone)]
      (ct/to-time-zone d timezone))
    d))

(defn from-timezone [d timezone]
  (if timezone
    (let [timezone (if (string? timezone)
                      (ct/time-zone-for-id timezone)
                      timezone)]
      (ct/from-time-zone d timezone))
    d))

#+clj
(defn normalize-date [d & [format timezone]]
  (when d
    (let [d (cond
              (instance? LocalDate d) (cc/to-date-time d)
              (instance? DateTime d) d
              (instance? java.util.Date d) (cc/from-date d)
              (integer? d) (cc/from-long d)
              (string? d) (try
                            (parse-date d format)
                            (catch Exception _))
              (map? d) (try
                         (let [year (Integer/valueOf (:year d (get d "year")))
                               month (Integer/valueOf (:month d (get d "month")))
                               day (Integer/valueOf (:day d (get d "day")))]
                           (ct/date-time year month day))
                         (catch Exception _))
              :else (throw (ex-info "Unrecognized date format" {:date d})))]
      (to-timezone d timezone))))

(defn to-date [d]
  (cc/to-date d))

(defn format-date [^DateTime d & [format]]
  (cf/unparse (cf/with-zone (cf/formatter (or format "yyyy-MM-dd")) (.getZone d))
              d))

(defn get-year-month-day [date]
  [(ct/year date)
   (ct/month date)
   (ct/day date)])

(defn parse-time [s]
  (try
    (cf/parse (cf/formatter "H:m") s)
    (catch Exception _
      (cf/parse (cf/formatter "H:m:s") s))))

(defn with-time [^DateTime datetime h m s]
  (.withTime datetime h m s 0))

(defn normalize-time [t]
  (when t
    (cond
      (instance? LocalTime t) (.toDateTime ^LocalTime t (ct/epoch))
      (instance? DateTime t) t
      (instance? java.util.Date t) (cc/from-date t)
      (string? t) (try
                    (parse-time t)
                    (catch Exception _))
      (map? t) (try
                 (let [h (Integer/valueOf (:h t (get t "h")))
                       ampm (:ampm t (get t "ampm"))
                       h (if ampm
                           (cond
                             (= 12 h) (if (= "am" ampm) 0 12)
                             (= "pm" ampm) (+ h 12)
                             :else h)
                           h)
                       m (Integer/valueOf (:m t (get t "m" 0)))
                       s (Integer/valueOf (:s t (get t "s" 0)))]
                   (with-time (ct/epoch) h m s))
                 (catch Exception _))
      :else (throw (ex-info "Unrecognized time format" {:time t})))))

(defn format-time [^DateTime t]
  (cf/unparse (cf/with-zone (cf/formatter "H:mm") (.getZone t))
              t))

(defn to-time [date]
  #+clj (java.sql.Time. (cc/to-long date))
  #+cljs date)

(defn hour [date]
  (ct/hour date))

(defn minute [date]
  (ct/minute date))

(defn sec [date]
  (ct/sec date))

(defn get-hours-minutes-seconds [date]
  [(hour date)
   (minute date)
   (sec date)])

(defn get-this-year []
  (ct/year (ct/now)))

(defn expand-name
  "Expands a name like \"foo[bar][baz]\" into [\"foo\" \"bar\" \"baz\"]"
  [name]
  (let [[_ name1 more-names] (re-matches #"^([^\[]+)((?:\[[^\]]+?\])*)$" name)]
    (if name1
      (if (seq more-names)
        (into [name1] (map second (re-seq #"\[([^\]]+)\]" more-names)))
        [name1])
      [name])))

(defn normalize-us-tel [v]
  (when-not (string/blank? v)
    (-> v
      (string/replace #"[^0-9x]+" "") ;only digits and "x" for extension
      (string/replace #"^1" "") ;remove leading 1
      )))

(defn valid-us-tel? [v]
  (and v (re-matches #"\d{10}(?:x\d+)?" (normalize-us-tel v))))

(defn format-us-tel [v]
  (if v
    (let [v* (normalize-us-tel (str v))]
      (if (valid-us-tel? v*)
        (let [[_ area prefix line ext] (re-find #"(\d\d\d)(\d\d\d)(\d\d\d\d)(?:x(\d+))?"
                                                v*)]
          (str "(" area ") " prefix "-" line
               (when ext (str " x" ext))))
        v))
    v))

(defn safe-element-id [id]
  (when id
    (string/replace id #"[^a-zA-Z0-9\-\_\:\.]" "__")))

(defn escape-html [s]
  (-> s
    (string/replace "&"  "&amp;")
    (string/replace "<"  "&lt;")
    (string/replace ">"  "&gt;")
    (string/replace "\"" "&quot;")))

(defn get-month-names []
  #+clj (.getMonths (java.text.DateFormatSymbols.))
  ;; TODO: i18n??
  #+cljs ["January" "February" "March" "April" "May" "June" "July"
          "August" "September" "October" "November" "December"])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Copied wholesale from ring.middleware.nested-params, for the sake of
;; ClojureScript support
;;

(defn parse-nested-keys
  "Parse a parameter name into a list of keys using a 'C'-like index
  notation. e.g.
    \"foo[bar][][baz]\"
    => [\"foo\" \"bar\" \"\" \"baz\"]"
  [param-name]
  (let [[_ k ks] (re-matches #"(.*?)((?:\[.*?\])*)" (name param-name))
        keys     (if ks (map second (re-seq #"\[(.*?)\]" ks)))]
    (cons k keys)))

(defn- assoc-nested
  "Similar to assoc-in, but treats values of blank keys as elements in a
  list."
  [m [k & ks] v]
  (conj m
        (if k
          (if-let [[j & js] ks]
            (if (= j "")
              {k (assoc-nested (get m k []) js v)}
              {k (assoc-nested (get m k {}) ks v)})
            {k v})
          v)))

(defn- param-pairs
  "Return a list of name-value pairs for a parameter map."
  [params]
  (mapcat
    (fn [[name value]]
      (if (sequential? value)
        (for [v value] [name v])
        [[name value]]))
    params))

(defn- nest-params
  "Takes a flat map of parameters and turns it into a nested map of
  parameters, using the function parse to split the parameter names
  into keys."
  [params parse]
  (reduce
    (fn [m [k v]]
      (assoc-nested m (parse k) v))
    {}
    (param-pairs params)))

(defn nested-params-request
  "Converts a request with a flat map of parameters to a nested map."
  [request & [opts]]
  (let [parse (:key-parser opts parse-nested-keys)]
    (update-in request [:params] nest-params parse)))