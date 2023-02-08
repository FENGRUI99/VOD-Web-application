const path = 'http://127.0.0.1:8080/';

fetch(path + "status/request")
    .then(response => response.json())
    .then(data => {
        console.log(data);
        const loadingBar = document.getElementById("loadingBar");
        const loadingPercentage = document.getElementById("loadingPercentage");
        const table = document.getElementById("dataTable");

        for (let i = 0; i < data.length; i++) {
            const item = data[i];
            const row = table.insertRow();

            const fileNameCell = row.insertCell();
            fileNameCell.innerHTML = item.fileName;

            const fileSizeCell = row.insertCell();
            fileSizeCell.innerHTML = item.fileSize;

            const bitRateCell = row.insertCell();
            bitRateCell.innerHTML = item.bitRate;
        }

        const percentage = data.progress;
        loadingBar.style.width = `${percentage}%`;
        loadingPercentage.innerHTML = `${percentage}%`;
    })
    .catch(error => {
        console.error(error);
    });






