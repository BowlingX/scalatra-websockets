$(function () {

    var options = { url: location.protocol + "//" + window.location.host + '/at/chat',
        contentType: "application/json",
        logLevel: 'debug',
        transport: 'long-polling',
        fallbackTransport: 'long-polling',
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