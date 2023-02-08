const path = 'http://127.0.0.1:8080/';

fetch(path + "status/request")
    .then(response => response.json())
    .then(data => {
        const loadingBar = document.getElementById("loadingBar");
        const loadingPercentage = document.getElementById("loadingPercentage");
        // const table = document.getElementById("dataTable");

        const fileName = document.getElementById("fileName");
        fileName.innerHTML = "File Name: " + data.fileName;

        const start = document.getElementById("start");
        start.innerHTML = "Start: " + data.start + " bytes";

        const fileSize = document.getElementById("fileSize");
        fileSize.innerHTML = "File Size: " + data.fileSize + " bytes";

        const bitRate = document.getElementById("bitRate");
        bitRate.innerHTML = "Bit Rate: " + data.bitRate + " bps";

        const percentage = (data.progress * 100).toFixed(2);

        const per = document.getElementById("per");
        per.innerHTML = "Percentage: " + percentage + " %";

        loadingBar.style.width = `${percentage}%`;
        loadingPercentage.innerHTML = `${percentage}%`;
    })
    .catch(error => {
        console.error(error);
    });






