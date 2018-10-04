
angular.module('resparql', [])
.factory('ResparqlService', ['$q', '$rootScope', function($q, $rootScope) {
  var Service = {};
  var callbacks = {};
  var currentCallbackId = 0;
  var ws = new WebSocket("ws://" + location.hostname + ":" + location.port + "/learning/");

  ws.onopen = function() {
    console.log("ws.onopen");
  };

  ws.onmessage = function(message) {
    console.log('ws.onmessage: ', message);
    listener(JSON.parse(message.data));
  };

  function sendRequest(request) {
    console.log('ResparqlService.sendRequest()');
    var defer = $q.defer();
    var callbackId = getCallbackId();
    callbacks[callbackId] = {
      time: new Date(),
      cb: defer
    };
    request.callback_id = callbackId;
    console.log('ResparqlService.sendRequest: Sending request', request);
    ws.send(JSON.stringify(request));
    console.log('ResparqlService.sendRequest: Sent request', defer.promise);
    return defer.promise;
  }

  function listener(data) {
    console.log("ResparqlService.listener()");
    var messageObj = data;
    console.log("ResparqlService.listener: Received data from websocket: ", messageObj);
    console.log('ResparqlService.listener: callbacks = ', callbacks);

//    var log = document.getElementById('log');
//    var content = document.createTextNode( JSON.stringify(messageObj, null, 2) + '\n' );
//    log.appendChild(content);

    // If an object exists with callback_id in our callbacks object, resolve it
    if(callbacks.hasOwnProperty(messageObj.callback_id)) {
      console.debug('ResparqlService.listener: found callback');
      console.log('ResparqlService.listener: ' + callbacks[messageObj.callback_id]);
      $rootScope.$apply(callbacks[messageObj.callback_id].cb.resolve(messageObj));

      delete callbacks[messageObj.callbackID];
    } else {
      console.error('unexpected');
    }

    console.log('ResparqlService.listener: command = ' + messageObj.command);
  }

  function getCallbackId() {
    currentCallbackId = (currentCallbackId + 1) % 10000;
    return currentCallbackId;
  }

  // Main service call for reverse engineering
  Service.executeRevEng = function(message) {
    console.log('ResparqlService.executeRevEng()');
    var promise = sendRequest(message);
    return promise;
  }

  Service.executeGetNegs = function(message) {
    console.log('ResparqlService.executeGetNegs()');
    var promise = sendRequest(message);
    return promise;
  }

  Service.searchEntity = function(message) {
    console.log('ResparqlService.searchEntity()');
    var promise = sendRequest(message);
    return promise;
  }

  Service.executeQuery = function(message) {
    console.log('ResparqlService.executeQuery()');
    var promise = sendRequest(message);
    return promise;
  }

  return Service;
}])
.controller('QueryTextController', function($scope, ResparqlService) {
  var sController = this;

  sController.pnExamplesText = "{}";
  sController.pnExamplesQuickText = '';
  sController.learnedQuery = '';
  sController.entityPairs = [];
  sController.revengQueryBindings = [];
  sController.revengExtraBindings = [];
  sController.defaultUriPrefix = 'http://dbpedia.org/resource/';
  sController.badUris = [];
  sController.badUrisText = '';

  function getRandomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
  }

  sController.searchEntity = function() {
    console.debug('sController.searchEntity()');
    message = { command: 'search', queryText: sController.keywordInputText };
    var promise = ResparqlService.searchEntity(message);
    console.debug('message supposedly sent');
    console.debug(promise);

    promise.then(function(val){
      console.debug('sController.searchEntity::callback() val = ', val);

      var pairs = val.pairs;

      for (var pair in pairs) {
        console.log("Key is " + pair + ", value is ", pairs[pair]);
      }

      sController.entityPairs = val.pairs;
    });
  };

  sController.executeQuery = function() {
    console.log('sController.executeQuery()');
    message = { command: 'execute', queryText: sController.learnedQuery };
    // console.debug('sending message: ' + JSON.stringify(message))
    // var promise = ResparqlService.executeQuery(message);

    // var message = { queryText: queryText.learnedQuery + '\nlimit 10', command: 'execute' }
    var promise = ResparqlService.executeQuery(message);
    console.debug('sController.executeQuery: sent execute message', message);
    promise.then(function(val) {
      console.debug('sController.executeQuery.promise.then()');
      sController.revengQueryBindings = val['virtuoso_response']['results']['bindings']
    });
  };

  sController.executeRevEngQuick = function() {
    console.debug('executeRevEngQuick()');

    sController.learnedQuery = '';

    var regex               = /^(\+|-)?(\w+)$/i;
    var regexUnaryMap       = /^(\+|-)?(\w+|<\S+>|\[\S+\])$/;
    var regexBinaryMappings = /^(\+|-)?{(\w+|<\S+>|\[\S+\]),(\w+|<\S+>|\[\S+\])?}$/;
    var regexBadUris        = /^(<\S+>)(\s+<\S+>)*$/;
    var varsList            = [];
    var positiveBindings    = [];
    var negativeBindings    = [];
    var badUris             = [];

    // parse bad uris:
    // var badUrisMatches = regexBadUris.exec(sController.badUrisText);
    var badUrisRaw = sController.badUrisText.split(/\s+/);
    console.debug('badUrisRaw = ', badUrisRaw);

    for (var i = 0; i < badUrisRaw.length; i++) {
      // if(!(badUris[i] === undefined)) {
        // badUris.push(badUrisMatches[i].replace(/^\s+|\s+$/gm,'').slice(1,-1));
        // replace
        badUris.push(badUrisRaw[i].slice(1,-1));
      // }
    }

    console.debug('badUris = ', badUris);

    // split the examples:
    var examplesArray = sController.pnExamplesQuickText.split(/\s+/);

    // use first example to determine arity:
    var arity = -1;
    var matches = regexUnaryMap.exec(examplesArray[0]);
    if(matches) {
      arity = 1;
    } else {
      matches = regexBinaryMappings.exec(examplesArray[0]);
      if(matches) { arity = 2; }
    }

    console.debug('arity = ', arity);

    var regex = null;
    if(arity === 1) {
      regex = regexUnaryMap;
      varsList = ['x'];
    } else {
      regex = regexBinaryMappings;
      varsList = ['x', 'y'];
    }

    examplesArray.map(function(obj) {
      var matches = regex.exec(obj);
      console.debug('parsed example: ', matches);

      if(!matches) {
        console.error('example doesn\'t match!');
      }

      var signText = matches[1];

      var sign = signText;
      if(!(sign === '+' || sign === '-')) {
        console.error('unknown example type');
      }

      // choose which bindings list to place this example:
      var correctBindings = (sign === '+') ? positiveBindings : negativeBindings;

      var textToUri = function(str) {
        if(str === undefined) { return undefined; }
        if(str.slice(0, 1) === '[' && str.slice(-1) === ']') {
          return sController.defaultUriPrefix + str.slice(1,-1);
        } else if(str.slice(0, 1) === '<' && str.slice(-1) === '>') {
          return str.slice(1,-1);
        } else {
          return sController.defaultUriPrefix + str;
        }
      };

      if(arity == 1) {
        var uri = textToUri(matches[2]);
        correctBindings.push({ x: { type: 'uri', value: uri } });
      } else if(arity == 2) {
        var uri1 = textToUri(matches[2]);
        var uri2 = textToUri(matches[3]);
        var binding = { };
        binding["x"] = { type: 'uri', value: uri1 };
        if(!(uri2 == undefined)) {
          binding["y"] = { type: 'uri', value: uri2 };
        }
        console.warn('binding = ', binding);
        correctBindings.push(binding);
      }
    });

    console.debug('executeRevEngQuick: posBindings = ', positiveBindings);
    console.debug('executeRevEngQuick: negBindings = ', negativeBindings);
    console.debug('executeRevEngQuick: badUris     = ', badUris);

    message = {
      command: 'reveng',
      pBindings: {
        head: { vars: varsList },
        results: { bindings: positiveBindings }
      },
      nBindings: {
        head: { vars: varsList },
        results: { bindings: negativeBindings }
      },
      badUris: badUris
    };

    console.debug('sending message: ' + JSON.stringify(message));
    sController.learnedQuery = 'Calculating...'

    ResparqlService
    .executeRevEng(message)
    .then(function(val) {
      console.log('executeRevEngQuick.then()', val);
      sController.learnedQuery = val.learnedQuery;
      sController.urisUsed = val.urisUsed;

      if(sController.learnedQuery === '') {
        sController.learnedQuery = 'NOTHING LEARNED';
      } else {
        // Get some potential negative examples.

        var message = {
          command: 'getnegs',
          queryText: sController.learnedQuery
        };

        console.log('executeRevEngQuick.then: sent getnegs message', message);

        sController.revengQueryBindings = [];

        ResparqlService
        .executeGetNegs(message)
        .then(function(val) {
          console.log('executeRevEngQuick.then.then');
          // sController.learnedQueryResults = "" + JSON.stringify(val['virtuoso_response']['results'], null, 2);
          sController.revengQueryBindings = val['virtuoso_response']['results']['bindings'].slice(0, 10);
          sController.binMappings = val['virtuoso_response']['head']['vars'].length == 3;

          console.log('sController.binMappings = ', sController.binMappings);
          console.log('setting sController.revengQueryBindings = ', val['virtuoso_response']['results']['bindings']);
        });

        // [OLD IMPLEMENTATION] PREPARE A RELAXED VERSION OF THE LEARNED QUERY:
//        console.log('Triples in working query: ', val.triples.length);
//        var eliminateTuple = getRandomInt(0, val.triples.length - 1) // Math.random() * val.triples.length
//        console.error('will remove: ', eliminateTuple);
//        var relaxed = 'select * from <http://dbpedia.org/> where { ';
//        for(var k in val.triples) {
//          console.debug('k: ', k);
//
//          if(k != eliminateTuple) {
//            var s = val.triples[k].s;
//            var p = val.triples[k].p;
//            var o = val.triples[k].o;
//            if(s.slice(0, 1) != '?') { s = '<' + s + '>'; }
//            if(p.slice(0, 1) != '?') { p = '<' + p + '>'; }
//            if(o.slice(0, 1) != '?') { o = '<' + o + '>'; }
//            relaxed += s + ' ' + p + ' ' + o + ' . ';
//          }
//        }
//        relaxed += '?x <http://www.w3.org/2000/01/rdf-schema#label> ?label . { select ?x (MIN(STR(?type)) as ?minType) where { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?type } group by ?x } } limit 10';

//        console.log('relaxed query built: ', relaxed);

        var relaxedQueryString = val.relaxedQuery;

        relaxedHacked = relaxedQueryString.substring(0, relaxedQueryString.length - 3);
        relaxedHacked += ' { select ?x (MIN(STR(?type)) as ?minType) where { ?x <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?type } group by ?x } }';

        console.warn(relaxedHacked);

        var message3 = {
          command: 'execute',
//          queryText: relaxedQueryString
          queryText: relaxedHacked
        }

        ResparqlService
        .executeQuery(message3)
        .then(function(val) {
          console.debug('promise3');
          sController.revengExtraBindings = val['virtuoso_response']['results']['bindings'].slice(0, 10)
        });

      }

    });
    // console.debug(promise);
  };

  sController.addTypeRestr = function(type) {
    console.error('sController.addTypeRestr(): ', type);
    // sController.learnedQuery.replace(/}/,'');
    sController.learnedQuery = sController.learnedQuery.replace(/\s*}\s*$/,'\n') + '\n. ?s a <' + type + '>\n}';
  }

  sController.addForbiddenURI = function(str) {
    sController.badUrisText += ' <' + str + '>';
  }

  sController.appendNSol = function(value) {
    console.debug('appendNSol', value);
    if(sController.binMappings) {
      sController.pnExamplesQuickText += ' -{<' + value.x.value + '>,<' + value.y.value + '>}';
    } else {
      sController.pnExamplesQuickText += ' -<' + value.x.value + '>';
    }

    console.debug('bla');
  }

  sController.appendPSol = function(value) {
    console.debug('appendPSol', value);
    if(sController.binMappings) {
      sController.pnExamplesQuickText += ' +{<' + value.x.value + '>,<' + value.y.value + '>}';
    } else {
      sController.pnExamplesQuickText += ' +<' + value.x.value + '>';
    }
  }

  sController.copyUri = function(value) {
    console.debug('copyUri', value);
    copyTextToClipboard(value);
  }

  sController.fillExample1 = function(value) {
    sController.pnExamplesQuickText = '+Chile +Bolivia +Venezuela +Spain -Brazil -Angola';
  }

  sController.fillExample2 = function(value) {
    sController.pnExamplesQuickText = '+Chile +Bolivia +Venezuela -Spain';
  }

  sController.fillExample3 = function(value) {
    sController.pnExamplesQuickText = '+{<http://dbpedia.org/resource/C._S._Lewis>,<http://dbpedia.org/resource/Oxford>}\n\n+{<http://dbpedia.org/resource/J._R._R._Tolkien>,<http://dbpedia.org/resource/Dorset>}\n\n+{<http://dbpedia.org/resource/J._K._Rowling>,}';
  }

});


function copyTextToClipboard(text) {
  var textArea = document.createElement("textarea");

  //
  // *** This styling is an extra step which is likely not required. ***
  //
  // Why is it here? To ensure:
  // 1. the element is able to have focus and selection.
  // 2. if element was to flash render it has minimal visual impact.
  // 3. less flakyness with selection and copying which **might** occur if
  //    the textarea element is not visible.
  //
  // The likelihood is the element won't even render, not even a flash,
  // so some of these are just precautions. However in IE the element
  // is visible whilst the popup box asking the user for permission for
  // the web page to copy to the clipboard.
  //

  // Place in top-left corner of screen regardless of scroll position.
  textArea.style.position = 'fixed';
  textArea.style.top = 0;
  textArea.style.left = 0;

  // Ensure it has a small width and height. Setting to 1px / 1em
  // doesn't work as this gives a negative w/h on some browsers.
  textArea.style.width = '2em';
  textArea.style.height = '2em';

  // We don't need padding, reducing the size if it does flash render.
  textArea.style.padding = 0;

  // Clean up any borders.
  textArea.style.border = 'none';
  textArea.style.outline = 'none';
  textArea.style.boxShadow = 'none';

  // Avoid flash of white box if rendered for any reason.
  textArea.style.background = 'transparent';


  textArea.value = text;

  document.body.appendChild(textArea);

  textArea.select();

  try {
    var successful = document.execCommand('copy');
    var msg = successful ? 'successful' : 'unsuccessful';
    console.log('Copying text command was ' + msg);
  } catch (err) {
    console.log('Oops, unable to copy');
  }

  document.body.removeChild(textArea);
}
