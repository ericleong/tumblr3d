![a tumblr cardboard icon](app/src/main/res/drawable-xxhdpi/ic_launcher.png?raw=true)

Tumblr3D
========

Tumblr photo posts for [Google Cardboard](http://g.co/cardboard), with support for gifs.

Originally created during Tumblr's Fall 2014 HackDay.

![a cat gif](tumblr3d.gif?raw=true)

Updated with search and TV mode during Tumblr's Spring 2016 HackDay.

build
-----
To run this project, you need API keys from [Tumblr](https://www.tumblr.com/oauth/apps).

Create a `local.properties` file in the root of the project with these values

```
tumblrConsumerKey=""
tumblrSecretKey=""
```

libraries
---------
* [Volley](https://android.googlesource.com/platform/frameworks/volley/)
* [Glide](https://github.com/bumptech/glide)
* [Jumblr](https://github.com/tumblr/jumblr)
* [Cardboard](https://developers.google.com/cardboard/overview)
