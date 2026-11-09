// O download passa pelo HttpClient, que envia o token; aqui só se entrega o arquivo ao navegador.
window.finanTrackDownload = (fileName, contentType, bytes) => {
    const url = URL.createObjectURL(new Blob([bytes], { type: contentType }));
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
};
