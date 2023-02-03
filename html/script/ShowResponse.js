const receivedTextData = document.getElementById("receivedTextData");
receivedTextData.innerHTML = sessionStorage.getItem("textdata");

fetch('http://172.16.7.10:10007/' + receivedTextData)
    .then(response => {
        const contentType = response.headers.get("Content-Type");
        if (contentType.includes("text/plain")) {
            return response.text();
        } else if (contentType.includes("image/jpeg") || contentType.includes("image/png")) {
            return response.blob();
        } else if (contentType.includes("audio/ogg")) {
            return response.blob().then(blob => {
                const audio = new Audio();
                audio.src = URL.createObjectURL(blob);
                audio.controls = true;
                return audio;
            });
        } else if (contentType.includes("video/ogg")) {
            return response.blob().then(blob => {
                const video = document.createElement("video");
                video.src = URL.createObjectURL(blob);
                video.controls = true;
                return video;
            });
        } else {
            throw new Error("Unsupported content type: " + contentType);
        }
    })
    .then(data => {
        const receivedData = document.getElementById("receivedData");
        if (data instanceof Blob) {
            const image = new Image();
            image.src = URL.createObjectURL(data);
            receivedData.appendChild(image);
        } else if (data instanceof Audio || data instanceof HTMLVideoElement) {
            receivedData.appendChild(data);
        } else {
            receivedData.innerHTML = data;
        }
    })
    .catch(error => {
        console.error(error);
    });
