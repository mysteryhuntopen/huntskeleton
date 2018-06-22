/* Operate the Mission Objectives pane */

$(document).ready(function() {
  setupMissionObjectives();
  setupFilmLinks();
});

function setupMissionObjectives() {
  reorderObjectives();
  fadeObjectives();
  $(window).resize(fadeObjectives);
  $('.objectives-workspace .objectives-list').scroll(fadeObjectives);
  $('.objectives-workspace .objectives-list-up a').on({
    'click': function(e) {
      e.preventDefault();
    },
    'mousedown touchstart': function () {
      $(".objectives-workspace .objectives-list").animate({scrollTop: 0}, 1000);
    },
    'mouseup touchend': function () {
      $(".objectives-workspace .objectives-list").stop(true);
    }
  });
  $('.objectives-workspace .objectives-list-down a').on({
    'click': function(e) {
      e.preventDefault();
    },
    'mousedown touchstart': function () {
      $(".objectives-workspace .objectives-list").animate({
        scrollTop: $(".objectives-workspace .objectives-list").prop('scrollHeight') - $(".objectives-workspace .objectives-list").outerHeight()
      }, 1000);
    },
    'mouseup touchend': function () {
      $(".objectives-workspace .objectives-list").stop(true);
    }
  });
  $('.objectives-workspace .objectives-list-content .objective').click(function() {
    selectObjective($(this).attr('data-objective-id'));
  });
}

function fadeObjectives() {
  var listBox = $('.objectives-workspace .objectives-list');
  var listBoxInner = $('.objectives-workspace .objectives-list-content');
  $('.objectives-workspace .objectives-list-up').toggleClass('at-edge',listBox.scrollTop() == 0);
  /* > height - 1 instead of == height to make it work in Edge */
  $('.objectives-workspace .objectives-list-down').toggleClass('at-edge',listBox.scrollTop()+listBox.outerHeight() > listBox.prop('scrollHeight') - 1);
  var topFade = listBox.position().top + parseFloat(listBox.css('margin-top'));
  var bottomFade = topFade + listBox.outerHeight(false);
  var topBand = parseFloat(listBoxInner.css('padding-top'));
  var bottomBand = parseFloat(listBoxInner.css('padding-bottom'));
  $('.objectives-workspace .objectives-list-content .objective').each(function(index) {
    var obj = $(this);
    var top = obj.position().top + parseFloat(obj.css('margin-top'));
    var bottom = top + obj.outerHeight(false);
    var opacity = 1.0;
    if (top <= topFade || bottom >= bottomFade) {
      opacity = 0;
    } else if (top < topFade + topBand) {
      opacity = (top - topFade) / topBand;
    } else if (bottom > bottomFade - bottomBand) {
      opacity = (bottomFade - bottom) / bottomBand;
    }
    obj.css({'opacity':opacity});
  });
}

/* Sort complete objectives to the bottom and automatically activate the first objective on the list */
/* Default activation can be overridden by passing a hash with the objective name*/
/* It's tricky to sort the objectives in the server template, so we just serve them in order and rearrange them on the client */
/* CSS sets objectives-container invisible to avoid flash of original order and lack of selection */
function reorderObjectives() {
  $('.objectives-workspace .objectives-list-content .objective.complete').appendTo('.objectives-workspace .objectives-list-content');
  var activeId = $('.objectives-workspace .objectives-list-content .objective').first().attr('data-objective-id');
  var hash = window.location.hash.replace('#', '');
  if ($('.objectives-workspace .objectives-list-content .objective[data-objective-id="'+hash+'"]').length) {
    activeId = hash;
  }
  selectObjective(activeId);
  $('.objectives-workspace .objectives-container').css({'visibility':'visible'});
}

function selectObjective(id) {
  $('.objectives-workspace .objective').removeClass('active');
  var obj = $('.objectives-workspace .objective[data-objective-id="'+id+'"]')
  if (obj.length) {
    obj.addClass('active');
    var name = obj.find('.objective-name').html();
    var body = obj.find('.objective-body').html();
    $('.objectives-workspace .objective-text-content .objective-name').html(name);
    $('.objectives-workspace .objective-text-content .objective-body').html(body);
    $('.objectives-workspace .objective-text').scrollTop(0);
    window.location.hash = '#' + id;
  }
}

function setupFilmLinks() {
  var scrim = $('<div class="objectives-scrim hidden"></div>');
  scrim.append('<div class="objectives-video-wrapper"></div>');
  scrim.append('<div class="objectives-video-closer">&#x2715;</div>');
  $('body').append(scrim);
  scrim.click(function(e) {
    e.preventDefault();
    hideVideo();
  });
  $('.objective-film-link').click(function(e) {
    var id = $(this).attr('data-video-id');
    if (id) {
      e.preventDefault();
      showVideo(id);
    }
  });
}

function showVideo(id) {
  $('.objectives-scrim .objectives-video-wrapper iframe').remove();
  var video = $('<div></div>')
  var scrim = $('.objectives-scrim').first();
  scrim.find('.objectives-video-wrapper').append(video);
  var player = new YT.Player(video[0], {
    videoId: id,
    width: '100%',
    height: '100%',
    playerVars: {
      'autoplay': 1,
      'controls': 1,
      'color': 'white',
      'fs': 1,
      'loop': 0,
      'modestbranding': 1,
      'playsinline': 1,
      'rel': 0,
      'showinfo': 0,
    },
    events: {
      'onStateChange': function(e) {
        if (e.data==YT.PlayerState.ENDED) {
          hideVideo();
        }
      },
    }
  });
  $(document).on('keyup.objective-video',function(e) {
    if (e.keyCode == 27) {
      hideVideo();
    }
  });
  scrim.removeClass('hidden');
}

function hideVideo() {
  $(document).unbind('keyup.objective-video')
  var scrim = $('.objectives-scrim').first();
  scrim.addClass('hidden');
  window.setTimeout(function(){$('.objectives-scrim .objectives-video-wrapper iframe').remove();}, 500);
}