(ns fix-youtube-mtime.core
  (:require [clojure.string :as str]
            [clj-http.client :as http-client]
            [clojure.data.json :as json]
            [clojure.instant :as time]
            [clojure.java.io :as io])
  (:import [java.util Calendar$Builder]
           [java.util Date Calendar$Builder]
           (java.io File))
  (:gen-class))

(defrecord update-success [was-successful file-name msg])

(def client-key-file-name ".client-key")

(defn load-client-key [file-name]
  "Load the client key from a file.
   The file is assumed to contain just the client key, with a possible trailing newline."
  (str/trim (slurp (io/resource file-name))))

(defn get-video-id-from-filename [file-name]
  "Gets the video ID from the filename.
   Videos downloaded by youtube-dl have a hyphen, followed by the 11-character video ID, followed by the extension.
   Videos downloaded by yt-dlp enclose the video ID in square brackets, followed by the extension."
  (let [youtube-dl-match (re-find #"-([A-Za-z0-9-]{11})\.\w{3,4}" file-name)
        yt-dlp-match (re-find #"\[([A-Za-z0-9-]{11})\]\.\w{3,4}" file-name)]
    (cond
      youtube-dl-match (youtube-dl-match 1)
      yt-dlp-match (yt-dlp-match 1)
      :else nil)))

(defn get-video-upload-date [client-key video-id]
  "Calls the YouTube API to retrieve the upload date for a video.
   CLIENT-KEY is the API key (see LOAD-CLIENT-KEY). VIDEO-ID is the ID of the YouTube videos, an 11-character string."
  (let [http-response (http-client/get "https://youtube.googleapis.com/youtube/v3/videos"
                                       {:accept :json
                                        :query-params {
                                                       :key client-key
                                                       :id video-id
                                                       :part "snippet"}})
        body (if (= (:status http-response) 200)
               (json/read-str (:body http-response) :key-fn keyword)
               nil)]
    (if body
      (time/read-instant-date (:publishedAt (:snippet ((:items body) 0))))
      nil)))

(defn date-to-millis [^Date date]
  "Convert DATE to a Unix timestamp."
  (-> (Calendar$Builder.)
      (.setInstant date)
      (.build)
      (.getTimeInMillis)))

(defn set-file-mtime [^File file ^Date upload-date]
  "Set the mtime of FILE to the timestamp represented by UPLOAD-DATE."
  (.setLastModified file (date-to-millis upload-date)))

(defn file-or-directory [^File f]
  "Determines if File F is a regular file or a directory.
   Used as the dispatch fn for the UPDATE-MTIME multimethod."
  (if (.isDirectory f)
    :directory
    :file))

(defmulti update-mtime
          "Updates the mtime of a file or directory. If a file is provided, it retrieves the YouTube video ID from the
           file name, calls the YouTube API and sets the mtime of the file to the upload date of the corresponding
           video. If a directory is specified, it iterates through the files in the directory, updating the mtimes of
           all the files corresponding to YouTube videos in that directory."
          file-or-directory)

(defmethod update-mtime :directory [dir]
  "Directories not handled yet")

(defmethod update-mtime :file [f]
  (let [api-key (load-client-key client-key-file-name)
        video-id (get-video-id-from-filename (.getName f))
        upload-date (if video-id
                      (get-video-upload-date api-key video-id)
                      nil)]
    (if upload-date
      (if (.setLastModified f (date-to-millis upload-date))
        (->update-success true (.getName f) "Update successful")
        (->update-success false (.getName f) "Was not able to update mtime on file"))
      (->update-success false (.getName f) "Was not able to retrieve mtime"))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
