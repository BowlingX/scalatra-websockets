$(function () {

    var options = { url: location.protocol + "//" + window.location.host + '/at/chat',
        contentType: "application/json",
        logLevel: 'debug',
        transport: 'websocket',
        fallbackTransport: 'long-polling',
        onMessage: function (response) {
            var message = response.responseBody;
            try {
                console.log(message);
            } catch (e) {
                return false;
            }

        } ,
        onOpen: function() {

        }
    };

    subsocket = $.atmosphere.subscribe(options);



});