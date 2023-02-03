var oInp = document.getElementById('inp');
var oBtn = document.getElementById('btn');

oBtn.onclick = function () {
    SendToServer();
}

document.onkeydown = function () {
    if (event.keyCode == 13) {
        SendToServer();
    }
}


function SendToServer() {
    fetch('http://172.16.7.10:10007/'+ oInp.value)
        .then(response => response.text())
        .then(data => console.log("Success:", data))
        .catch(error => console.error("Error:", error));
}