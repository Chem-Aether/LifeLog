<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup>

</script>

<style>
:root {
  --el-color-primary: #198b88;
  --el-color-primary-light-3: #55aaa5;
  --el-color-primary-light-5: #83c1bd;
  --el-color-primary-light-7: #b1d8d5;
  --el-color-primary-light-8: #c8e4e1;
  --el-color-primary-light-9: #e1f0ee;
  --el-color-primary-dark-2: #126d6b;
  color: #263a42;
  background: #f3f6f7;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

* { box-sizing: border-box; }

html, body, #app {
  width: 100%;
  min-width: 320px;
  min-height: 100%;
}

body {
  margin: 0;
  font-family: Inter, "Microsoft YaHei", "PingFang SC", sans-serif;
  background: #f3f6f7;
}
</style>
