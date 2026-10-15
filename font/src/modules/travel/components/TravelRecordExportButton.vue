<template>
  <FlightBoardingPassExport
    v-if="type === 'flight'"
    :record-id="recordId"
    :record="record"
  />
  <TrainTicketExport
    v-else
    :record-id="recordId"
    :record="record"
  />
</template>

<script setup>
import FlightBoardingPassExport from './FlightBoardingPassExport.vue'
import TrainTicketExport from './TrainTicketExport.vue'

defineProps({
  type: { type: String, required: true, validator: value => ['flight', 'train'].includes(value) },
  recordId: { type: [String, Number], required: true },
  record: { type: Object, required: true },
})
</script>
