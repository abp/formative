(ns formative.render-form.bootstrap
  (:require [formative.render-form :refer [render-form*]]
            [formative.render-field :refer [render-field]]
            [formative.helpers :refer [render-problems]]))

(def ^:dynamic *field-prefix* "field-")

(defn render-bootstrap-row [field]
  (let [field-id (if (:id field)
                   (name (:id field))
                   (str *field-prefix* (:name field)))
        field (assoc field :id field-id)
        field (if (= :submit (:type field))
                (assoc field :class (str (:class field)
                                         " btn btn-primary"))
                field)]
    [:div {:id (str "row-" field-id)
           :class (str (if (= :submit (:type field))
                         "control-group submit-group "
                         "control-group field-group ")
                       (name (:type field :text)) "-row"
                       (when (:problem field) " error problem"))}
     (if (= :heading (:type field))
       [:legend (render-field field)]
       (list
         [:div {:class (if (#{:checkbox :submit} (:type field))
                         "empty-shell"
                         "label-shell")}
          (when (and (not (#{:checkbox} (:type field))) (:label field))
            [:label.control-label {:for field-id}
             (:label field)])]
         [:div.input-shell.controls
          (when (:prefix field)
            [:span.prefix (:prefix field)])
          (if (= :checkbox (:type field))
            [:label.checkbox {:for field-id} " "
             (render-field field) " "
             [:span.cb-label (:label field)]]
            (render-field field))
          (when (:suffix field)
            [:span.suffix (:suffix field)])
          (when (and (= :submit (:type field))
                     (:cancel-href field))
            [:span.cancel-link " " [:a.btn {:href (:cancel-href field)}
                                    "Cancel"]])
          (when (:note field)
            [:div.note.help-inline (:note field)])]))]))

(defn render-bootstrap-form [form-attrs fields class opts]
  (let [[hidden-fields visible-fields] ((juxt filter remove)
                                        #(= :hidden (:type %)) fields)
        submit-only? (and (= 1 (count visible-fields))
                          (= :submit (:type (first visible-fields))))
        shell-attrs {:class (str class
                                 (when submit-only? " submit-only"))}
        shell-attrs (if (:id form-attrs)
                      (assoc shell-attrs :id (str (name (:id form-attrs))
                                                  "-shell"))
                      shell-attrs)]
    [:div shell-attrs
     (when-let [problems (:problems opts)]
       (when (map? (first problems))
         (render-problems problems fields)))
     [:form (dissoc form-attrs :renderer)
      (list
       (map render-field hidden-fields)
       [:fieldset
        (map render-bootstrap-row visible-fields)])]]))

(defmethod render-form* :bootstrap-horizontal [form-attrs fields opts]
  (render-bootstrap-form form-attrs fields "form-shell form-horizontal" opts))

(defmethod render-form* :bootstrap-stacked [form-attrs fields opts]
  (render-bootstrap-form form-attrs fields "form-shell" opts))