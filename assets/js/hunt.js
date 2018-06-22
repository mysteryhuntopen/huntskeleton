$(function () {
  setupTooltips();

  var toggleDropdown = function() {
    $(this).toggleClass('modal-open');
    $(this).closest('.header-dropdown').find('.dropdown-content-wrapper').fadeToggle(150)
  }

  $('.header-dropdown-title').click(function() {
    var self = this;
    toggleDropdown.bind(this).call();
    setTimeout(function() {
      $(document).one('click', toggleDropdown.bind(self));
    }, 0);
    return false;
  });
})

$.preloadImage = function(url) {
  $(function() {
    $("<img/>").attr("src", url);
  });
}

var setupTooltips = function() {
  /* Sets up default behaviors.  Override as needed with .tooltip('option',...) */
  var content = function() {
    var elem = $(this);
    var title = elem.attr("title");
    var answer = elem.attr("data-answer");
    var annotation = elem.attr("data-annotation");
    var tooltip = '<div class="tooltip-title">' + title +'</div>';
    if (annotation !== undefined && annotation.length > 0) {
      tooltip = '<div class="tooltip-annotation">' + annotation +'</div>' + tooltip;
    }
    if (answer !== undefined && answer.length > 0) {
      tooltip = tooltip + '<div class="tooltip-answer answer">' + answer +'</div>';
    }
    return tooltip;
  }
  var classes = { "ui-tooltip": "" };
  var duration = 300;
  var placements = {
    "bottom": { my: "center top+10", at: "center bottom", collision: "flipfit" },
    "top": { my: "center bottom-10", at: "center top", collision: "flipfit" },
    "right": { my: "right-10 center", at: "left center", collision: "flipfit" },
    "left": { my: "left-10 center", at: "right center", collision: "flipfit" },
    "center": { my: "center center", at: "center center", collision: "flipfit" },
  }

  $('[data-toggle="tooltip"][title]').tooltip({
    content: content,
    track: false,
    position: placements['bottom'],
    classes: classes,
    show: duration,
    hide: duration,
  })

  $('[data-toggle="tooltip"][data-placement="track"]').tooltip('option', {
    'track':true,
    'position': { my: "center top+20", at: "center bottom"},
  });

  $.each(placements, function(placement, position) {
    $.each(['auto ', ''], function( i, auto) {
      $('[data-toggle="tooltip"][data-placement="' + auto + placement + '"]').tooltip('option', 'position', position);
    });
  });

  $('[data-tooltip-class]').each(function(index) {
    $(this).tooltip('option', 'classes.ui-tooltip', $(this).attr('data-tooltip-class'));
  });
}