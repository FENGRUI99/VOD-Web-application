const receivedTextData = document.getElementById("receivedTextData");
receivedTextData.innerHTML = sessionStorage.getItem("textdata");

fetch('http://172.16.7.10:10007/' + receivedTextData)
    .then(response => response.arrayBuffer())
    .then(arrayBuffer => {
        const base64 = btoa(
            new Uint8Array(arrayBuffer).reduce(
                (data, byte) => data + String.fromCharCode(byte),
                ""
            )
        );
        const img = document.getElementById("receivedImage");
        img.src = "data:image/png;base64," + base64;
    });
