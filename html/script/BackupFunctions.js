// open a web page:
// var url = 'https://www.google.com/search?q=' + oInp.value;
// window.open(url);

// fetch with json:
// fetch("http://172.16.7.10:10007", {
//     method: "POST",
//     headers:{
//         "Content-Type": "application/json"
//     },
//     body: JSON.stringify(data)
// })

// fetch with text:
// fetch('http://172.16.7.10:10007/', {
//     method: "POST",
//     headers:{
//         "Content-Type": "text/plain"
//     },
//     body: oInp.value
// })

// SendToServer function:
// document.onkeydown = function () {
//     if (event.keyCode == 13) {
//         SendToServer();
//     }
// }


// function SendToServer() {
//     fetch('http://172.16.7.10:10007/'+ oInp.value)
//         .then(response => response.text())
//         .then(data => console.log("Success:", data))
//         .catch(error => console.error("Error:", error));
// }
