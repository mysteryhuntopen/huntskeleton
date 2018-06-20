'use strict';

import fs from 'fs';
import path from 'path';

import browserSync from 'browser-sync';
import del from 'del';
import gulp from 'gulp';
import mergeStream from 'merge-stream';
import pump from 'pump';

import autoprefixer from 'gulp-autoprefixer';
import buffer from 'gulp-buffer';
import changed from 'gulp-changed';
import cssnano from 'gulp-cssnano';
import environments from 'gulp-environments';
import filter from 'gulp-filter';
import retinize from 'gulp-retinize';
import rev from 'gulp-rev';
import revReplace from 'gulp-rev-replace';
import sass from 'gulp-sass';

const paths = {
  assets: 'assets',
  dist: 'dist',
  build: 'build',

  allAssets: 'assets/**',
  images: 'assets/**/*.+(png|jpg|gif|svg)',
  sass: 'assets/**/*.+(scss|sass)',
  css: 'assets/**/*.css',

  manifest: 'build/rev-manifest.json',
};

const transformPath = newPath => {
  if (!environments.production()) {
    return newPath;
  }

  const relativePath = path.relative(paths.dist, newPath);

  let manifestString = null;
  try {
    manifestString = fs.readFileSync(path.join(paths.build, 'rev-manifest.json'));
  } catch (e) {
    // If we can't read the manifest, then we need to process the file
    return newPath + '+force';
  }

  const manifest = JSON.parse(manifestString);
  if (!(relativePath in manifest)) {
    return newPath + '+force';
  }

  return path.resolve(paths.dist, manifest[relativePath]);
};

const finalize = () => [
  environments.production(rev()),
  gulp.dest(paths.dist),
  environments.production(rev.manifest(paths.manifest, {
    base: paths.build,
    merge: true,
  })),
  environments.production(gulp.dest(paths.build)),
  bs.stream(),
];

const bs = browserSync.create();

gulp.task('default', () => {
  const imageFilter = filter(paths.images, {restore: true});
  const sassFilter = filter(paths.sass, {restore: true});
  const cssFilter = filter(paths.css, {restore: true});

  return pump([
    gulp.src(paths.allAssets),
    environments.development(changed(paths.dist, {transformPath})),

    // images
    imageFilter,
    retinize(),
    buffer(),
    imageFilter.restore,

    // sass
    sassFilter,
    sass(),
    sassFilter.restore,

    // css (including processed sass)
    cssFilter,
    autoprefixer(),
    cssnano({safe: true}),
    cssFilter.restore,

    environments.production(rev()),
    revReplace(),
    gulp.dest(paths.dist),
    environments.production(rev.manifest(paths.manifest, {
      base: paths.build,
      merge: true,
    })),
    environments.production(gulp.dest(paths.build)),
    bs.stream(),
  ]);
});
gulp.task('watch', ['default'], () => {
  gulp.watch(paths.allAssets, { usePolling: true, interval: 2000 }, ['default'])
});

gulp.task('serve', ['watch'], () => {
  bs.init({
    server: paths.dist,
    port: 5003,
    socket: {
      domain: 'localhost:5003',
    },
    online: false,
    open: false,
    cors: true,
  });
});

gulp.task('clear', () => {
  return del([paths.dist, paths.build]);
});
