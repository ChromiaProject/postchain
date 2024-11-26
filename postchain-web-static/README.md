# Web static GTX module

An GTX module to serve static web content from the blockchain. Accessing the root will give you `index.html`.

Enable and configure it by putting this into `chromia.yml`:

```yaml
blockchains:
  my_rell_dapp:
    module: main
    config:
      gtx:
        modules:
          - "net.postchain.web.WebStaticGTXModuleFactory"
      web_static:
        cache_ttl_seconds: 3600 # 60 (one minute) by default
        content:
          index.html:
            content_type: text/html
            content: >
              <!DOCTYPE html>
              <html lang="en">
                <head>
                  <link rel="stylesheet" href="css/style.css">
                  <title>My Rell Dapp</title>
                </head>
                <body>
                  <h1>My Rell Dapp</h1>
                  <p><image src="img/image.png"></p>
                </body>
              </html>
          css/style.css: 
            content_type: text/css
            content: >
              h1 {
                text-color: green;
              }
          img/image.png: 
            content_type: image/png
            content: x"1234ABCD"
```
