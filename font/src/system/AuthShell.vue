<template>
  <main class="auth-page">
    <section class="auth-shell">
      <aside class="brand-panel">
        <RouterLink class="brand-mark" to="/login" aria-label="LifeLog 登录">
          <span class="brand-symbol">L</span>
          <span>LifeLog</span>
        </RouterLink>

        <div class="brand-copy">
          <span class="brand-eyebrow">PERSONAL ARCHIVE</span>
          <h1>把走过的路，<br>和认真生活的每一天，<br>都好好记下来。</h1>
          <p>行程、账单与生活记录，在这里慢慢汇成自己的时间线。</p>
        </div>

        <div class="brand-footer">
          <span>LifeLog</span>
          <span>记录真实生活</span>
        </div>
      </aside>

      <section class="auth-content">
        <slot />
      </section>
    </section>
  </main>
</template>

<script setup>
import { Coin, Position } from '@element-plus/icons-vue'
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background: #eef3f4;
}

.auth-shell {
  width: min(1080px, 100%);
  min-height: min(680px, calc(100vh - 64px));
  display: grid;
  grid-template-columns: 1.02fr 0.98fr;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dbe4e6;
  border-radius: 8px;
  box-shadow: 0 16px 48px rgba(25, 58, 65, 0.08);
}

.brand-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 40px 44px 28px;
  color: #f6fbfa;
  background: #155b5d;
}

.brand-mark {
  width: fit-content;
  display: inline-flex;
  align-items: center;
  gap: 11px;
  color: inherit;
  font-size: 19px;
  font-weight: 700;
  text-decoration: none;
}

.brand-symbol {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.46);
  border-radius: 8px;
  color: #fff;
  font-family: Georgia, serif;
  font-size: 21px;
}

.brand-copy {
  margin-top: auto;
  padding: 50px 0 28px;
}

.brand-eyebrow {
  color: #b9dbd5;
  font-size: 11px;
  font-weight: 700;
}

.brand-copy h1 {
  margin: 17px 0 15px;
  color: #fff;
  font-size: 29px;
  font-weight: 600;
  line-height: 1.62;
}

.brand-copy p {
  max-width: 360px;
  margin: 0;
  color: #d0e5e1;
  font-size: 14px;
  line-height: 1.8;
}

.brand-highlights {
  display: grid;
  gap: 10px;
  padding: 20px 0 30px;
  border-top: 1px solid rgba(255, 255, 255, 0.17);
}

.highlight-row {
  display: grid;
  grid-template-columns: 30px 1fr auto;
  align-items: center;
  gap: 10px;
  color: #f4faf8;
  font-size: 13px;
}

.highlight-row small {
  color: #b9d8d3;
  font-size: 12px;
}

.highlight-icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  font-size: 15px;
}

.travel-icon { color: #d4eee6; background: rgba(83, 177, 151, 0.25); }
.bill-icon { color: #ffe1b3; background: rgba(225, 157, 75, 0.22); }

.brand-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.17);
  color: #b9d8d3;
  font-size: 11px;
}

.brand-footer span:first-child { color: #f4faf8; font-weight: 600; }

.auth-content {
  display: grid;
  align-content: center;
  min-width: 0;
  padding: 64px clamp(32px, 7vw, 82px);
}

@media (max-width: 760px) {
  .auth-page { display: block; padding: 12px; }
  .auth-shell {
    min-height: calc(100dvh - 24px);
    grid-template-columns: 1fr;
  }
  .brand-panel { padding: 22px 24px 19px; }
  .brand-copy { padding: 30px 0 20px; }
  .brand-copy h1 { margin: 11px 0 8px; font-size: 22px; line-height: 1.45; }
  .brand-copy p { font-size: 13px; }
  .brand-highlights { display: none; }
  .brand-footer { padding-top: 12px; }
  .auth-content { padding: 34px 24px 42px; }
}

@media (max-width: 380px) {
  .auth-content { padding-inline: 18px; }
  .brand-panel { padding-inline: 19px; }
}
</style>
