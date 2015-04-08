---
layout: default
title:  "hello"
section: "home"
---
# Getting Started

## Generating the Site

run `sbt docs/makeSite`

## Editing docs

* The source for the static pages is in `docs/src/site`
* The source for the tut compiled pages is in `docs/src/main/tut`

## Previewing the site

1. Install jekyll locally, depending on your platform, you might do this with:

    brew install jekyll

    yum install jekyll

    apt-get install jekyll

    gem install jekyll

2. In a shell, navigate to the generated site directory in `docs/target/site`

3. Start jekyll with `jekyll serve`

4. Navigate to http://localhost:4000/indoctrinate/ in your browser

5. Make changes to your site, and run `sbt makeSite` to regenerate the site. The changes should be reflected as soon as you run `makeSite`.

## Publishing the site to github.

You must first edit `docs/build.sbt` to customize the `git.remoteRepo` variable to point to your repository, then run `sbt ghpagesPushSite`


# Examples

You can look at [this example](tut/example.html) for an example of a tut compiled example document. [Read more about tut](tut.html)

