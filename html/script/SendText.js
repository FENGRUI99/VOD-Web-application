const oInp = document.getElementById('inp');
const oBtn = document.getElementById('btn');
const peers = [];
const path = 'http://127.0.0.1:8080/';

oBtn.onclick = function () {
    convertToRightType();
    if(oInp.value.startsWith("peer/add")) {
        displayPeers();
    } else{
        skipToResponse();
    }
    oInp.value= "";
}

document.onkeydown = function () {
    if (event.keyCode == 13) {
        convertToRightType();
        if(oInp.value.startsWith("peer/add")) {
            displayPeers();
        } else{
            skipToResponse()
        }
        oInp.value= "";
    }
}

function displayPeers(){
    document.getElementById('peers').innerText = "Peers info: "
    peers.push(oInp.value);
    for(let i = 0; i < peers.length; i++){
        document.getElementById('peers').innerText += "\n" + (i+1) + ". " + peers[i];
    }
    fetch(path + oInp.value)
        .catch(error => console.error("Error:", error));
}

function skipToResponse(){
    sessionStorage.setItem("textData", oInp.value);
    // window.location.href = "showResponse.html";
    window.location.href = "http://localhost:8080/" + oInp.value;
}

function convertToRightType(){
    // const ipAddressRegex = /^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])/;
    if(oInp.value.startsWith("/")){
        oInp.value = oInp.value.substring(1);
        convertToRightType();
    }
    // else if(oInp.value.startsWith("http://")){
    //     oInp.value = oInp.value.substring(7);
    //     convertToRightType();
    // }else if(ipAddressRegex.test(oInp.value)){
    //     //TODO: remove all with port number
    //     oInp.value = oInp.value.replace(ipAddressRegex, "");
    //     convertToRightType();
    // }
}