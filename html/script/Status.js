const path = 'http://127.0.0.1:8080/';

fetch(path + "status/request")
    .then(response => response.json())
    .then(data => {
        //TODO: finish static loading bar

        const table = document.getElementById('dataTable');

        data.forEach(item => {
            const row = table.insertRow();

            const fileNameCell = row.insertCell();
            fileNameCell.innerHTML = item.fileName;

            const fileSizeCell = row.insertCell();
            fileSizeCell.innerHTML = item.fileSize;

            const bitRateCell = row.insertCell();
            bitRateCell.innerHTML = item.bitRate;
        });
    })
    .catch(error => {
        console.error(error);
    });

