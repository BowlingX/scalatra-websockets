$(function () {

    var options = { url: location.protocol + "//" + window.location.host + '/at/chat',
        contentType: "application/json",
        logLevel: 'debug',
        transport: 'websocket',
        fallbackTransport: 'long-polling',
        maxReconnectOnClose: 200,
        onMessage: function (response) {
            var message = response.responseBody;
            try {
                $("#messages").prepend(message + "<br>");
            } catch (e) {
                return false;
            }

        } ,
        onOpen: function() {
        }
    };

    var subsocket = $.atmosphere.subscribe(options);

     $("#sendMessage").click(function(){
         subsocket.push($("#inputMessage").val());
     });

});