Hunt 2018
=========

This is pretty incomplete, but hopefully helps with getting the basics
working.

Components
----------

There are 4 basic components:

 - `cube`: The Java REST API. This is responsible for tracking the state of the hunt
 - `present`: The main frontend/site
 - `submit`: A separate app for submitting answers/callins
 - `admin`: Internal management interface

Setup
-----

Run `docker-compose up --build` to start all of the various
services. To access the web services, go to:

 - `present`: http://localhost:5000
 - `submit`: http://localhost:5001 (mostly only works with links from `present`)
 - `admin`: http://localhost:5002

Logins
------

The development config has a handful of pre-configured usernames.

 - `adminuser` / `adminpassword`
 - `writingteamuser` / `writingteampassword`
 - `testerteam` / `testerteampassword`
 - `testerteamN` / `testerteamNpassword` (for `N` from 2-70)

(Note: testerteam passwords aren't consistent in formatting with admin
or writingteam passwords)

All 3 web services use the same session cookie, so if you're logged
into one you're logged into all. Which is a bit silly, since if you
can login to `admin`, your account isn't able to use `present` and
vice versa.

(Incognito sessions are really useful for this)

You can always logout by going to http://localhost:5002/logout

"Starting" Hunt
---------------

For development, `cube` uses an in-memory database that's wiped every
time you restart it. Before you can test `present`, you have to start
hunt.

Login to `admin` using `adminuser`, click "Admin Tools", and click the
two buttons under "Start Hunt".

You'll have to do this every time `cube` starts.
