// TFMCDev01 integration checks. Run on the Dev host after installing both PR jars:
// node /path/to/Magic/tools/dev-admin-values.cjs
// Uses the existing Dev-only bot/console helpers and a dedicated test character.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const { joinAsCharacter } = require('/home/fran/bot-lib/character.js')
const consoleCmd = require('/home/fran/bot-lib/console.js')
const B = 'AdminValueProbe'
const MC = '/home/fran/work/amp/TFMCDev01/Minecraft'
const LOG = `${MC}/logs/latest.log`
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const readSince = start => fs.readFileSync(LOG).subarray(start).toString('utf8')
const results = []
let bot, character
const chat = []

async function consoleRun(command, delay = 500) {
  consoleCmd(command)
  await sleep(delay)
}
async function command(command, expected) {
  const start = chat.length
  bot.chat('/' + command)
  for (let i = 0; i < 40; i++) {
    await sleep(100)
    const messages = chat.slice(start)
    if (messages.some(line => expected.test(line))) return messages.join('\n')
  }
  throw new Error(`/${command}: expected ${expected}, received ${JSON.stringify(chat.slice(start))}`)
}
function profile() {
  return JSON.parse(fs.readFileSync(`${MC}/plugins/Magic/data/characters/${character}.json`, 'utf8'))
}
function focus() {
  return JSON.parse(fs.readFileSync(`${MC}/plugins/RPCharacters/data/focus/${character}.json`, 'utf8'))
}
async function step(name, fn) {
  await fn()
  results.push(name)
  console.log('PASS ' + name)
}
async function hold(pattern) {
  const slot = bot.inventory.slots.findIndex((item, i) => i >= 36 && item && pattern.test(JSON.stringify(item.components ?? item.nbt)))
  assert.ok(slot >= 36, 'expected test item in hotbar: ' + pattern)
  bot.setQuickBarSlot(slot - 36)
  await sleep(400)
}

;(async () => {
  const logStart = fs.statSync(LOG).size
  try {
    bot = await joinAsCharacter(B, { class: /Mage/ })
    bot.on('messagestr', line => { chat.push(line); if (!/❤|^\s*$/.test(line)) console.log('CHAT ' + line) })
    await sleep(2000)
    await consoleRun(`deop ${B}`)
    await step('unprivileged character cannot change Magic or Focus', async () => {
      await command(`magic equilibrium set ${B} 10`, /do not have permission/i)
      await command(`focus set ${B} 10`, /do not have permission|Unknown or incomplete command/i)
    })
    await consoleRun(`op ${B}`)
    await step('player inspection identifies the active character', async () => {
      const reply = await command(`magic player get ${B}`, /character [0-9a-f-]+/i)
      character = reply.match(/character ([0-9a-f-]+)/i)[1]
      assert.ok(profile())
    })
    await step('equilibrium set/add/remove clamp, persist and reject invalid numbers', async () => {
      await command(`magic equilibrium set ${B} -30`, /equilibrium -30/)
      assert.equal(profile().equilibrium, -30)
      await command(`magic equilibrium add ${B} 10`, /equilibrium -20/)
      await command(`magic equilibrium remove ${B} 5`, /equilibrium -25/)
      await command(`magic equilibrium set ${B} 1e300`, /equilibrium 100/)
      for (const amount of ['NaN', 'Infinity', 'bad'])
        await command(`magic equilibrium set ${B} ${amount}`, /finite amount/i)
      await command(`magic equilibrium remove ${B} -1`, /finite amount/i)
      await command(`magic equilibrium reset ${B}`, /equilibrium 0/)
    })
    await step('cast mode set/get/reset and invalid-mode validation', async () => {
      await command(`magic castmode set ${B} surge`, /casting surge/)
      assert.equal(profile().castMode, 'surge')
      await command(`magic castmode get ${B}`, /casting surge/)
      await command(`magic castmode set ${B} bogus`, /Unknown casting mode/)
      await command(`magic castmode reset ${B}`, /casting flow/)
    })
    await step('resonance explicit removal, all-school edits and bounds', async () => {
      await command(`magic resonance set ${B} cerrith 30`, /resonance/i)
      assert.equal(profile().resonance.cerrith, 30)
      await command(`magic resonance remove ${B} cerrith 12`, /resonance/i)
      assert.equal(profile().resonance.cerrith, 18)
      await command(`magic resonance remove ${B} cerrith -1`, /amount|number/i)
      await command(`magic resonance add ${B} all 1000`, /resonance/i)
      assert.equal(profile().resonance.cerrith, 100)
      await command(`magic resonance reset ${B}`, /reset/i)
      assert.equal(profile().resonance.cerrith ?? 0, 0)
      await command(`magic resonance set ${B} cerrith 10 extra`, /Usage:/)
      assert.equal(profile().resonance.cerrith ?? 0, 0)
    })
    await step('Focus set/add/remove/reset persists actual clamped balance', async () => {
      await command(`focus set ${B} 30`, /30\//)
      assert.equal(focus().points, 30)
      await command(`focus add ${B} -10`, /20\//)
      await command(`focus remove ${B} 5`, /15\//)
      assert.equal(focus().points, 15)
      await command(`focus add ${B} 2147483647`, /Focus for/)
      assert.ok(focus().points > 0)
      await command(`focus remove ${B} 2147483647`, /0\//)
      for (const amount of ['1.5', '2147483648']) await command(`focus set ${B} ${amount}`, /whole points/)
      await command(`focus remove ${B} -1`, /whole points/)
      await command(`focus reset ${B}`, /Focus for/)
    })
    await step('Magic player reset preserves Focus', async () => {
      await command(`focus set ${B} 42`, /42\//)
      await command(`magic equilibrium set ${B} 50`, /equilibrium 50/)
      await command(`magic player reset ${B}`, /equilibrium 0/)
      assert.equal(focus().points, 42)
      assert.equal(profile().castMode, 'flow')
    })
    await step('console can edit the loaded player and rejects missing targets', async () => {
      const start = fs.statSync(LOG).size
      await consoleRun(`magic equilibrium set ${B} -12`)
      assert.equal(profile().equilibrium, -12)
      await consoleRun('magic player get NonexistentProbe')
      assert.match(readSince(start), /not found|not online|unknown player|find player/i)
      await command(`magic equilibrium reset ${B}`, /equilibrium 0/)
    })
    await consoleRun(`clear ${B}`)
    await step('held-item commands reject empty hands', async () => {
      await command('magic artifact inspect', /Hold an identified artifact/)
      await command('magic gear inspect', /Hold an identified artifact/)
    })
    await consoleRun(`magic artifact give ${B} cerrith common`, 1200)
    await hold(/artifact_id/)
    await step('artifact fill and muffle support get/set/add/remove/reset', async () => {
      await command('magic artifact inspect', /Artifact:/)
      await command('magic artifact fill set cerrith 8', /cerrith aura: 8\//)
      await command('magic artifact fill remove cerrith 3', /cerrith aura: 5\//)
      await command('magic artifact fill add all 1', /cerrith aura: 6\//)
      await command('magic artifact fill reset all', /cerrith aura: 0\//)
      await command('magic artifact muffle set 25', /muffle: 25/)
      await command('magic artifact muffle remove 5', /muffle: 20/)
      await command('magic artifact muffle add 10', /muffle: 30/)
      await command('magic artifact muffle get', /muffle: 30/)
      await command('magic artifact muffle reset', /muffle: 0/)
    })
    await step('artifact wear can be repaired and then crumbled', async () => {
      await command('magic artifact wear set 3', /wear: 3/)
      await command('magic artifact wear remove 1', /wear: 2/)
      await command('magic artifact wear reset', /wear: 0/)
      await command('magic artifact wear add 100000', /wear:/)
      await command('magic artifact inspect', /Hold an identified artifact/)
    })
    await consoleRun(`clear ${B}`)
    await consoleRun(`magic weapon give ${B} wand mitlan 70 iron_wand_core oak_handle`, 1500)
    await hold(/gear_archetype/)
    await step('weapon Rift and remaining durability edit without losing the weapon', async () => {
      await command('magic gear inspect', /rift:/)
      await command('magic gear rift set 10', /rift: 10/)
      await command('magic gear rift add 5', /rift: 15/)
      await command('magic gear rift remove 2', /rift: 13/)
      await command('magic gear rift reset', /rift: 0/)
      await command('magic gear durability set 20', /durability: 20/)
      await command('magic gear durability remove 100000', /durability: 1\b/)
      await command('magic gear durability reset', /durability: (?:[2-9]|[1-9]\d+)\b/)
      await command('magic gear rift set 1.5', /whole numbers/)
      await command('magic gear inspect', /attunement.mitlan:/)
    })
    await step('edits survive logout and rejoin', async () => {
      await command(`magic resonance set ${B} cerrith 23`, /resonance/i)
      await command(`magic castmode set ${B} surge`, /casting surge/)
      await command(`focus set ${B} 37`, /37\//)
      bot.quit(); await sleep(2000)
      bot = await joinAsCharacter(B, { class: /Mage/ })
      bot.on('messagestr', line => chat.push(line))
      await command(`magic player get ${B}`, /casting surge/)
      assert.equal(profile().resonance.cerrith, 23)
      await command(`focus get ${B}`, /37\//)
    })
    await step('Dev log contains no command exceptions', async () => {
      assert.doesNotMatch(readSince(logStart), /Unhandled exception executing command|Could not pass event.*(?:Magic|RPCharacters)|\[Magic\].*(?:Failed to|Exception)/)
    })
    fs.writeFileSync(__dirname + '/dev-admin-values-results.json', JSON.stringify({ server: 'TFMCDev01', character, passed: results }, null, 2))
    console.log(`PASS ${results.length} integration groups`)
  } catch (error) {
    console.error('FAIL ' + error.stack)
    process.exitCode = 1
  } finally {
    if (bot && character) {
      try {
        await consoleRun(`magic player reset ${B}`)
        await consoleRun(`focus restore ${B}`)
        await consoleRun(`clear ${B}`)
      } catch (error) { console.error('Cleanup: ' + error.message); process.exitCode = 1 }
    }
    try { await consoleRun(`deop ${B}`) } catch (error) { console.error('Deop cleanup: ' + error.message); process.exitCode = 1 }
    if (bot) bot.quit()
    await sleep(1000)
    process.exit(process.exitCode || 0)
  }
})()
