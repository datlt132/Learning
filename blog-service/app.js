const express = require("express")

const app = express();

app.use("/blog", (req, res) => {
    return res.status(200).json({
        message: "Blog service"
    })
})

app.use("/content", (req, res) => {
    return res.status(200).json({
        message: "Blog service"
    })
})

app.listen(9000, async () => {
    console.log('Web server started at port 9000!');
});


module.exports = app;