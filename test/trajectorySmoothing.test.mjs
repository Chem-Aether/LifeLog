import assert from 'node:assert/strict'
import test from 'node:test'
import { smoothTrajectoryGeometry, smoothTrajectoryLine } from '../font/src/modules/travel/utils/trajectorySmoothing.js'

test('rounds a dense corner without changing its endpoints or input', () => {
  const line = [[0, 0, 10], [0.0005, 0, 20], [0.0005, 0.0005, 30]]
  const result = smoothTrajectoryLine(line)
  assert.deepEqual(result, [line[0], [0.000375, 0, 17.5], [0.0005, 0.000125, 22.5], line[2]])
  assert.deepEqual(line[1], [0.0005, 0, 20])
})

test('keeps long gaps and separate segments unchanged', () => {
  const sparse = [[0, 0], [0.01, 0], [0.0101, 0.0001]]
  assert.deepEqual(smoothTrajectoryLine(sparse), sparse)
  const geometry = { type: 'MultiLineString', coordinates: [[[0, 0], [0.0005, 0], [0.0005, 0.0005]], [[1, 1], [2, 2]]] }
  const result = smoothTrajectoryGeometry(geometry)
  assert.equal(result.coordinates[0].length, 4)
  assert.deepEqual(result.coordinates[1], geometry.coordinates[1])
  assert.equal(geometry.coordinates[0].length, 3)
})

test('bounds work for very large routes', () => {
  const line = Array.from({ length: 4001 }, (_, index) => [index * 0.00001, 0])
  assert.equal(smoothTrajectoryLine(line), line)
})
