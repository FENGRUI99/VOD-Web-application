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
    // var url = 'https://www.google.com/search?q=' + oInp.value;
    // window.open(url);

    // fetch("http://172.16.7.10:10007", {
    //     method: "POST",
    //     headers:{
    //         "Content-Type": "application/json"
    //     },
    //     body: JSON.stringify(data)
    // })

    // fetch('http://172.16.7.10:10007/', {
    //     method: "POST",
    //     headers:{
    //         "Content-Type": "text/plain"
    //     },
    //     body: oInp.value
    // })
    fetch('http://172.16.7.10:10007/'+ oInp.value)
        .then(response => response.text())
        .then(data => console.log("Success:", data))
        .catch(error => console.error("Error:", error));
}