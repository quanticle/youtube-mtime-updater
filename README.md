# fix-youtube-mtime

A little script that will set the modified time of YouTube videos that have been downloaded with youtube-dl or yt-dlp.

## Installation

- First install [Leiningen](https://leiningen.org/)
- `git clone git@github.com:quanticle/youtube-mtime-updater`
- Get an [API Key](https://developers.google.com/youtube/registering_an_application) for YouTube
- Place the API key `resources/client-key`
- Build the code with `lein uberjar`

## Usage

The code reads a sequence of files or directories from standard input, delimited by newlines. If the input is a file, it will hit the YouTube API and update the mtime for that file. If the input is a directory, the code will recurse into the directory, updating the mtimes on all YouTube videos located in that directory (and subdirectories). The code assumes that the files have their YouTube video IDs in the file name, either in `youtube-dl` format (i.e. `<title>-<video_id>.<extension>`) or `yt-dlp` format (i.e. `<title> [<video_id>].<extension>`).

YouTube video titles often include Unicode characters outside of the normal ASCII range, so on Windows PowerShell, set the following options:

```
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
```

This works around a known issue with Java on Windows, where it assumes that input text is encoded using `Cp1252`, unless specifically told otherwise. The above step is not necessary on Linux or MacOS.


## License

Copyright © 2022 Rohit Patnaik

This program and the accompanying materials are made available under the
terms of the GNU General Public License 3.0 which is available at
https://www.gnu.org/licenses/gpl-3.0.en.html.
