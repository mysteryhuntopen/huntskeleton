#!/bin/sh

cd "$(dirname "$0")"
gsutil -m -h 'Cache-Control:max-age=0, must-revalidate' rsync -d -r assets gs://hunt2018-assets/
