var oInp = document.getElementById('inp');
var oBtn = document.getElementById('btn');

oBtn.onclick = function () {
    sessionStorage.setItem("textdata", oInp.value);
    window.location.href = "showResponse.html";
}

document.onkeydown = function () {
    if (event.keyCode == 13) {
        sessionStorage.setItem("textdata", oInp.value);
        window.location.href = "showResponse.html";
    }
}