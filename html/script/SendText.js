const oInp = document.getElementById('inp');
const oBtn = document.getElementById('btn');
const peers = [];

oBtn.onclick = function () {
    if(oInp.value.startsWith("peer/add")) {
        displayPeers();
    } else{
        skipToResponse();
    }
}

document.onkeydown = function () {
    if (event.keyCode == 13) {
        if(oInp.value.startsWith("peer/add")) {
            displayPeers();
        } else{
            skipToResponse()
        }
    }
}

function displayPeers(){
    document.getElementById('peers').innerText = "Peers info: "
    peers.push(oInp.value);
    for(let i = 0; i < peers.length; i++){
        document.getElementById('peers').innerText += "\n" + (i+1) + ". " + peers[i];
    }
    fetch('http://172.16.7.10:8080/'+ oInp.value)
        .catch(error => console.error("Error:", error));
}

function skipToResponse(){
    sessionStorage.setItem("textData", oInp.value);
    window.location.href = "showResponse.html";
}