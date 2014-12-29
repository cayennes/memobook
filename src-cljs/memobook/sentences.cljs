(ns memobook.sentences)

(defn parse [s]
  (->> s
       (re-seq #"([^＜｜＞<|>]+)|＜([^＜｜＞<|>]+)(?:｜([^＜｜＞<|>]*))(?:｜([^＜｜＞<|>]*))?＞")
       (mapv #(hash-map :text (or (nth % 1) (nth % 2))
                        :kana (nth % 3)
                        :definition (nth % 4)))))
(enable-console-print!)

(defn make-sentence [raw]
  {:data (parse raw) :page page :type :sentence})

(defn sentence-grouping-fn [k]
  (fn [v & existing-groups]
    (mapv #(assoc % k v) (apply concat existing-groups))))

(defn page [page & sentences]
  ((sentence-grouping-fn :page) page (mapv make-sentence sentences)))

(def chapter (sentence-grouping-fn :chapter))
(def volume (sentence-grouping-fn :volume))
(def title (sentence-grouping-fn :title))

(def sentences
  (title "よつばと！"
    (volume 1
      (chapter 1
        (page 13
          "なんか＜俺｜おれ＞＜体力｜たいりょく＞＜低下｜ていか｜decline＞してんなぁ"
          "おまえさ＜近所｜きんじょ＞にあいさつで＜配｜くば＞る＜粗品｜そしな｜small gift＞とか＜用意｜ようい｜preperation＞してるか？"
          "いやおまえの＜嗜好｜しこう｜taste/preference＞はいい")
        (page 16
          "ダンボールはですね＜資源ごみ｜しげんごみ｜recyclable garbage＞の＜日｜ひ＞に＜出｜だ＞すんです＜金曜日｜きんようび＞ですね" "あそこの＜電柱｜でんちゅう｜telephone pole＞の＜所｜ところ＞")
        (page 17
          "＜ちなみに｜｜by the way＞＜燃｜も＞えるゴミは＜月｜げつ＞＜木｜もく＞＜燃｜も＞えないゴミは＜土曜日｜どようび＞ですから"
          "＜他｜ほか＞にも＜何｜なに＞かわからない＜事｜こと＞＜等｜など＞あらましたら＜遠慮｜えんりょ｜hesitation＞なくおっしゃって＜下｜く＞ださい"
          "＜しっかりして｜｜be reliable/levelheaded＞んなぁ〜と＜思｜おも＞って"
          "＜しっかりして｜｜pull yourself together＞ください")
        (page 22
          "あれって…＜ブランコ｜｜swing＞？")
        (page 24
          "＜自分｜じぶん＞で＜反動｜はんどう｜opposite motion/recoil＞つけたら＜動｜うご＞くから")
        (page 31
          "＜あじさい｜｜hydrangea＞＜公園｜こうえん＞にいたよ")
        (page 38
          "＜全力｜ぜんりょく＞＜疾走｜しっそう｜sprint＞！？"))
      (chapter 2
        (page 67
          "そっとして＜おこう｜｜scandelous behavior＞")
        (page 76
          "そりゃさっさと＜交換｜こうかん｜replacement＞した＜方｜ほう＞がいいな")
        (page 83
          "＜怪しい｜あやしい｜suspicious＞と＜思｜おも＞われるでしょうがこれはよとばちゃんと＜留守番｜るすばん＞を"))
      (chapter 3
        (page 100
          "あ〜＜ちっこう｜｜very small＞の")
        (page 103
          "すご〜く＜おおまか｜｜rough sketch＞に＜言｜い＞うと〜"
          "＜北極｜ほっきょく＞の＜氷｜こおり＞がとけて＜島｜しま＞が＜沈んじゃったり｜しずんじゃったり｜sinking＞")
        (page 107
          "とーちゃん＜みそこなった｜｜misjudged＞")
        (page 110
          "＜最近｜さいきん＞の＜省電力｜しょうでんりょく｜conservation of electric power＞で＜地球｜ちきゅう＞にやさしいし")
        (page 112
          "この＜うっかり｜｜thoughtlessly＞さん"))
      (chapter 4
        (page 119
          "あはは＜大げさ｜おおげさ｜exaggerated＞な")
        (page 125
          "＜回覧板｜かいらんばん｜circular notice＞が＜回｜まわ＞ってきたりとか")
        (page 126
          "テレビなら＜家｜うち＞にひとつ＜余って｜あまって｜left over＞ますけど")
        (page 130
          "＜未確認飛行物体｜みかくにんひこうぶったい｜UFO＞＜発見｜はっけん＞！！")
        (page 138
          "＜俺｜おれ＞は＜裸｜はだか｜naked＞でも＜大丈夫｜だいじょうぶ＞ですから"
          "＜いかん｜｜regrettable＞！＜緊張｜きんちょう｜nervousness＞しまった！")))))

(print (count sentences) "sentences")
