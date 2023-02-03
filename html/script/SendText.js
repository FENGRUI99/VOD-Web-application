var oInp = document.getElementById('inp');
var oBtn = document.getElementById('btn');

oBtn.onclick = function () {
    sessionStorage.setItem("data", oInp.value);
    window.location.href = "showResponse.html";
}