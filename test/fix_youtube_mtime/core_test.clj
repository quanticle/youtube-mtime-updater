(ns fix-youtube-mtime.core-test
  (:require [clojure.test :refer :all]
            [mockery.core :refer :all]
            [clojure.instant :as time]
            [fix-youtube-mtime.core :refer :all])
  (:import (java.io File)))

(deftest extract-video-ids
  (testing "Extract youtube-dl formatted ID"
    (is (= (get-video-id-from-filename "The Fast And The Furious HD (Ten Second Car)-f5XuABX34qI.mkv")
       "f5XuABX34qI")))
  (testing "Extract yt-dlp formatted ID"
    (is (= (get-video-id-from-filename "The real reasons Alpine didn't want to sign Daniel Ricciardo for F1 2023 [fgHelV41-7I].webm")
       "fgHelV41-7I")))
  (testing "Extract yt-dlp formatted ID with underscore"
    (is (= (get-video-id-from-filename "The real reasons Alpine didn't want to sign Daniel Ricciardo for F1 2023 [fgHelV41_7I].webm")
           "fgHelV41_7I")))
  (testing "Extract yt-dlp formatted ID from a subtitle file"
    (is (= (get-video-id-from-filename "Primitive Technology： Stone Axe (celt) [BN-34JfUrHY].en.vtt")
           "BN-34JfUrHY")))
  (testing "Extract yt-dlp formatted ID from a title with hyphens"
    (is (= "CGE_GtD0koU"
           (get-video-id-from-filename "Do-It-Yourself ： Repainting Steel Entry Doors [CGE_GtD0koU].webm")))))

(deftest get-youtube-client-key
  (testing "Retrieve client key"
    (with-mocks [mock-slurp {:target :clojure.core/slurp :return "test-client-id\n"}
                 mock-resource {:target :clojure.java.io/resource :return "test resource data"}]
               (is (= (load-client-key "client-key") "test-client-id"))
               (is (= (:call-args @mock-slurp) '("test resource data"))))))

(deftest get-upload-date-for-video-id
  (testing "Get video upload date success"
    (with-mock mock-http
               {:target :clj-http.client/get
                :return {
                         :body "{
          \"kind\": \"youtube#videoListResponse\",
          \"etag\": \"KoEqrHAJXkKXjTxlKS4gtGzS7C8\",
          \"items\": [
            {
              \"kind\": \"youtube#video\",
              \"etag\": \"w9b_JnrCNWpWZJretU2__DJh2B0\",
              \"id\": \"jeztKFQBbI0\",
              \"snippet\": {
                \"publishedAt\": \"2016-05-18T18:15:07Z\",
                \"channelId\": \"UCvT5j1DeZn5JTZkdX5XAy6A\",
                \"title\": \"The Simpsons - Homer's Donut With Sprinkles\",
                \"description\": \"\",
                \"thumbnails\": {
                  \"default\": {
                    \"url\": \"https://i.ytimg.com/vi/jeztKFQBbI0/default.jpg\",
                    \"width\": 120,
                    \"height\": 90
                  },
                  \"medium\": {
                    \"url\": \"https://i.ytimg.com/vi/jeztKFQBbI0/mqdefault.jpg\",
                    \"width\": 320,
                    \"height\": 180
                  },
                  \"high\": {
                    \"url\": \"https://i.ytimg.com/vi/jeztKFQBbI0/hqdefault.jpg\",
                    \"width\": 480,
                    \"height\": 360
                  }
                },
                \"channelTitle\": \"Mostly Simpsons\",
                \"tags\": [
                  \"Simpsons\",
                  \"Homer\",
                  \"Apu\",
                  \"Donut\",
                  \"Sprinkles\"
                ],
                \"categoryId\": \"23\",
                \"liveBroadcastContent\": \"none\",
                \"localized\": {
                  \"title\": \"The Simpsons - Homer's Donut With Sprinkles\",
                  \"description\": \"\"
                }
              }
            }
          ],
          \"pageInfo\": {
            \"totalResults\": 1,
            \"resultsPerPage\": 1
          }
        }
        "
                         :status 200}}
               (is (= (get-video-upload-date "test key" "test video id") (time/read-instant-date "2016-05-18T18:15:07Z")))
               (is (= (:call-args @mock-http) ["https://youtube.googleapis.com/youtube/v3/videos"
                                          {:accept :json
                                           :query-params {:key "test key"
                                                          :id "test video id"
                                                          :part "snippet"}}]))))
  (testing "Get video upload date failure"
    (with-mock mock-http {:target :clj-http.client/get
                          :return {
                                   :status 403
                                   :body "Access denied"
                                   }}
               (is (= (get-video-upload-date "test key" "test video id") nil)))))

(deftest convert-date-to-millis
  (testing "Convert date"
    (is (= (date-to-millis (time/read-instant-date "2016-05-18T18:15:07Z")) 1463595307000))))

(deftest set-file-mtime-correctly
  (testing "Set file mtime correctly"
    (let [last-modified (atom 0)
          mock-file (proxy [File] ["foo"]
                      (setLastModified [timestamp]
                        (reset! last-modified timestamp)
                        true))]
      (set-file-mtime mock-file (time/read-instant-date "2016-05-18T18:15:07Z"))
      (is (= @last-modified 1463595307000)))))

(deftest test-file-or-directory
  (testing "File"
    (let [mock-file (proxy [File] ["foo"]
                      (isDirectory [] false))]
      (is (= (file-or-directory mock-file) :file))))
  (testing "Directory"
    (let [mock-file (proxy [File] ["foo"]
                      (isDirectory [] true))]
      (is (= (file-or-directory mock-file) :directory)))))

(deftest test-update-mtime-file
  (testing "Update a file's mtime successfully"
    (with-mocks [mock-load-client-key {:target :fix-youtube-mtime.core/load-client-key
                                       :return "test key"}
                 mock-get-video-id-from-filename {:target :fix-youtube-mtime.core/get-video-id-from-filename
                                                  :return "test video id"}
                 mock-get-video-upload-date {:target :fix-youtube-mtime.core/get-video-upload-date
                                             :return "test upload date"}
                 mock-date-to-millis {:target :fix-youtube-mtime.core/date-to-millis
                                      :return 10}]
      (let [last-mod-time (atom 0)
            mock-file (proxy [File] ["foo"]
                        (getName [] "test file name")
                        (setLastModified [last-modified]
                          (reset! last-mod-time last-modified)
                          true)
                        (isDirectory [] false))
            result (update-mtime mock-file)]
        (is (= (:was-successful result) true))
        (is (= @last-mod-time 10))
        (is (= (:call-args @mock-load-client-key) [client-key-file-name]))
        (is (= (:call-args @mock-get-video-id-from-filename) ["test file name"]))
        (is (= (:call-args @mock-date-to-millis) ["test upload date"]))
        (is (= (:call-args @mock-get-video-upload-date) ["test key" "test video id"])))))
    (testing "Unsuccessful mtime update"
      (with-mocks [mock-load-client-key {:target :fix-youtube-mtime.core/load-client-key
                                         :return "test key"}
                   mock-get-video-id-from-filename {:target :fix-youtube-mtime.core/get-video-id-from-filename
                                                    :return "test video id"}
                   mock-get-video-upload-date {:target :fix-youtube-mtime.core/get-video-upload-date
                                               :return "test upload date"}
                   mock-date-to-millis {:target :fix-youtube-mtime.core/date-to-millis
                                        :return 10}]
        (let [last-mod-time (atom 0)
              mock-file (proxy [File] ["foo"]
                          (getName [] "test file name")
                          (setLastModified [last-modified]
                            (reset! last-mod-time last-modified)
                            false)
                          (isDirectory [] false))
              result (update-mtime mock-file)]
          (is (= (:was-successful result) false))
          (is (= (:msg result) "Was not able to update mtime on file")))))
    (testing "Unsuccessful mtime retrieval"
      (with-mocks [mock-load-client-key {:target :fix-youtube-mtime.core/load-client-key
                                         :return "test key"}
                   mock-get-video-id-from-filename {:target :fix-youtube-mtime.core/get-video-id-from-filename
                                                    :return "test video id"}
                   mock-get-video-upload-date {:target :fix-youtube-mtime.core/get-video-upload-date
                                               :return nil}
                   mock-date-to-millis {:target :fix-youtube-mtime.core/date-to-millis
                                        :return 10}]
        (let [last-mod-time (atom 0)
              mock-file (proxy [File] ["foo"]
                          (getName [] "test file name")
                          (setLastModified [last-modified]
                            (reset! last-mod-time last-modified)
                            false)
                          (isDirectory [] false))
              result (update-mtime mock-file)]
          (is (= (:was-successful result) false))
          (is (= (:msg result) "Was not able to retrieve mtime"))))))

(deftest test-print-errors
  (testing "Print error if update-result is unsuccessful"
    (with-mocks [mock-print {:target :clojure.core/println}]
      (let [result (->update-success false
                                      (proxy [File] ["foo"]
                                              (getPath [] "C:\\foo"))
                                      "Update failed")]
        (is (= (report-errors result) "C:\\foo: Update failed\n"))))))
