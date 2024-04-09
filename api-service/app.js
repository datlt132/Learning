const express = require("express")

const app = express();

app.use("/api", (req, res) => {
    return res.status(200).json({
        message: "Api service"
    })
})

app.listen(8080, async () => {
    console.log('Web server started at port 8080!');
});


module.exports = app;