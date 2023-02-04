var oInp = document.getElementById('inp');
var oBtn = document.getElementById('btn');

oBtn.onclick = function () {
    if(oInp.value.startsWith("peer/add")) {
        document.getElementById('peers').innerText = "Peers info: " + oInp.value;
        fetch('http://172.16.7.10:8080/'+ oInp.value)
        .catch(error => console.error("Error:", error));
    } else{
        sessionStorage.setItem("textdata", oInp.value);
        window.location.href = "showResponse.html";
    }
}

document.onkeydown = function () {
    if (event.keyCode == 13) {
        if(oInp.value.startsWith("peer/add")) {
            document.getElementById('peers').innerText = "Peers info: " + oInp.value;
            fetch('http://172.16.7.10:8080/'+ oInp.value)
                .catch(error => console.error("Error:", error));
        } else{
            sessionStorage.setItem("textdata", oInp.value);
            window.location.href = "showResponse.html";
        }
    }
}