opening_scene_controller = {
	debugger = 0,

	on_spawn = function(mob)
		mob.side = 1
		mob:sendSide()
	end,

	move = function(mob, target)
		local opening = mob.registry["opening"]
		local decision = mob.registry["opening_decision_one"]
		local attacker = mob.registry["opening_attacker"]
		local xWarps = {67, 63, 40, 19, 8, 4}
		local yWarps = {59, 36, 9, 11, 21, 46}
		local newMobPlayer = {}
		local runScript = true
		local spawn = true
		local player = nil
		local magnus = nil

		--capricorn
		local isgar = nil

		--leo
		local tanna = nil

		--aquarius
		local hiro = nil

		--cancer
		local ichkin = nil

		--virgo
		local arelai = nil

		--libra
		local panda = nil

		--STANDIN :o
		local corthol = nil
		local warp = math.random(6)

		if (mob.registry["opening_player"] > 0) then
			player = Player(mob.registry["opening_player"])
		end

		if (mob.registry["opening_magnus"] > 0) then
			magnus = Mob(mob.registry["opening_magnus"])
		end

		if (mob.registry["opening_isgar"] > 0) then
			isgar = Mob(mob.registry["opening_isgar"])
		end

		if (mob.registry["opening_tanna"] > 0) then
			tanna = Mob(mob.registry["opening_tanna"])
		end

		if (mob.registry["opening_hiro"] > 0) then
			hiro = Mob(mob.registry["opening_hiro"])
		end

		if (mob.registry["opening_ichkin"] > 0) then
			ichkin = Mob(mob.registry["opening_ichkin"])
		end

		if (mob.registry["opening_arelai"] > 0) then
			arelai = Mob(mob.registry["opening_arelai"])
		end

		if (mob.registry["opening_panda"] > 0) then
			panda = Mob(mob.registry["opening_panda"])
		end

		if (mob.registry["opening_corthol"] > 0) then
			corthol = Mob(mob.registry["opening_corthol"])
		end

		if (attacker == 0) then
			mob.registry["opening_attacker"] = math.random(6)
		end

		--[[start points
	Hexagram X Coords: 6, 7, 8, 9, 10
	Hexagram Y Coords: 19, 20
	Tile Still: 35838
	Tile Spinning: 35836]]

		if (player == nil and mob.registry["opening"] > 0) then
			if (mob.registry["opening_wait"] == 0) then
				mob.registry["opening_wait"] = os.time()
			elseif (mob.registry["opening_wait"] + 30 <= os.time()) then
				mob.registry["opening"] = 0
				mob.registry["opening_wait"] = 0
			end
		end

		--[[for i = 1, #player do
		if (player[i].gmLevel >= 50) then
			runScript = false
		end
	end]]
		--

		if (opening_scene_controller.debugger > 0) then
			opening = opening_scene_controller.debugger
		end

		if (opening == 0) then
			if (player == nil) then
				newMobPlayer = mob:getObjectsInSameMap(BL_PC)
				newMobPlayer[1].side = 1
				newMobPlayer[1]:sendSide()
				mob.registry["opening_player"] = newMobPlayer[1].ID
			end

			mob:msg(
				0,
				"-------------------------------------------------------",
				player.ID
			)
			mob:msg(
				0,
				"---------------------Curtain Call----------------------",
				player.ID
			)
			mob:msg(
				0,
				"-------------------------------------------------------",
				player.ID
			)
		elseif (opening == 3) then
			mob:sendAnimationXY(416, 12, 22, 1)
		elseif (opening == 8) then
			if (magnus == nil) then
				mob:spawn(300000, 12, 22, 1)
				newMobPlayer = mob:getObjectsInSameMap(BL_MOB)

				for i = 1, #newMobPlayer do
					if (newMobPlayer[i].mobID == 300000) then
						newMobPlayer[i].side = 2
						newMobPlayer[i]:sendSide()
						mob.registry["opening_magnus"] = newMobPlayer[i].ID
					end
				end
			end
		elseif (opening == 9) then
			mob:sendAnimationXY(390, 4, 22, 1)
		elseif (opening == 16) then
			if (isgar == nil) then
				mob:spawn(300004, 4, 22, 1)
				newMobPlayer = mob:getObjectsInSameMap(BL_MOB)

				for i = 1, #newMobPlayer do
					if (newMobPlayer[i].mobID == 300004) then
						newMobPlayer[i].side = 2
						newMobPlayer[i]:sendSide()
						mob.registry["opening_isgar"] = newMobPlayer[i].ID
					end
				end
			end
		elseif (opening == 18) then
			magnus.side = 3
			magnus:sendSide()
			isgar.side = 0
			isgar:sendSide()
		elseif (opening == 21) then
			isgar.side = 1
			isgar:sendSide()
		elseif (opening == 22) then
			isgar:talk(0, "Isgar: Jadi, di mana Tanna?")
		elseif (opening == 26) then
			magnus:sendAction(20, 80)
		elseif (opening == 27) then
			mob:talk(
				3,
				player.name .. ": Siapa orang-orang ini? Apa yang mereka lakukan di sini?"
			)
		elseif (opening == 30) then
			mob:sendAnimationXY(427, 8, 16, 1)
		elseif (opening == 32) then
			if (tanna == nil) then
				mob:spawn(300002, 8, 16, 1)
				newMobPlayer = mob:getObjectsInSameMap(BL_MOB)

				for i = 1, #newMobPlayer do
					if (newMobPlayer[i].mobID == 300002) then
						newMobPlayer[i].side = 2
						newMobPlayer[i]:sendSide()
						mob.registry["opening_tanna"] = newMobPlayer[i].ID
					end
				end
			end
		elseif (opening == 35) then
			magnus:talk(0, "Magnus: Itu dia.")
		elseif (opening == 36) then
			isgar.side = 0
			isgar:sendSide()
			mob:moveGhost()
		elseif (opening == 43) then
			isgar:talk(0, "Isgar: Kau terlambat.")
			mob:moveGhost()
		elseif (opening == 46) then
			mob:moveGhost()
		elseif (opening == 51) then
			tanna:talk(0, "Tanna: Aku sedang menyiapkan segalanya.")
		elseif (opening == 53) then
			mob:moveGhost()
		elseif (opening == 54) then
			mob:sendAnimationXY(419, 4, 17, 1)
		elseif (opening == 56) then
			mob:talk(
				3,
				player.name .. ": Menyiapkan sesuatu? Sebaiknya aku bersembunyi."
			)
			mob:moveGhost()
		elseif (opening == 57) then
			if (hiro == nil) then
				mob:spawn(300003, 4, 17, 1)
				newMobPlayer = mob:getObjectsInSameMap(BL_MOB)

				for i = 1, #newMobPlayer do
					if (newMobPlayer[i].mobID == 300003) then
						newMobPlayer[i].side = 2
						newMobPlayer[i]:sendSide()
						mob.registry["opening_hiro"] = newMobPlayer[i].ID
					end
				end
			end
		elseif (opening == 59) then
			mob:moveGhost()
		elseif (opening == 61) then
			mob.side = 0
			mob:sendSide()
		elseif (opening == 62) then
			mob:sendAnimationXY(415, 12, 17, 1)
		elseif (opening == 63) then
			if (ichkin == nil) then
				mob:spawn(300001, 12, 17, 1)
				newMobPlayer = mob:getObjectsInSameMap(BL_MOB)

				for i = 1, #newMobPlayer do
					if (newMobPlayer[i].mobID == 300001) then
						newMobPlayer[i].side = 2
						newMobPlayer[i]:sendSide()
						mob.registry["opening_ichkin"] = newMobPlayer[i].ID
					end
				end
			end
		elseif (opening == 67) then
			mob:sendAnimationXY(322, 8, 23, 1)
		elseif (opening == 71) then
			if (arelai == nil) then
				mob:spawn(300005, 8, 23, 1)
				newMobPlayer = mob:getObjectsInSameMap(BL_MOB)

				for i = 1, #newMobPlayer do
					if (newMobPlayer[i].mobID == 300005) then
						newMobPlayer[i].side = 2
						newMobPlayer[i]:sendSide()
						mob.registry["opening_arelai"] = newMobPlayer[i].ID
					end
				end
			end
		elseif (opening == 75) then
			arelai.side = 0
			arelai:sendSide()
		elseif (opening == 77) then
			hiro:talk(0, "Hiro: Halo, Arelai.")
		elseif (opening == 85) then
			arelai:talk(0, "Arelai: Hey Ichkin.")
			arelai:sendAction(14, 80)
		elseif (opening == 88) then
			hiro.side = 1
			hiro:sendSide()
			hiro:sendAction(18, 80)
		elseif (opening == 93) then
			ichkin:talk(0, "Ichkin: Umm... hi?")
		elseif (opening == 96) then
			ichkin:sendAction(13, 80)
		elseif (opening == 103) then
			tanna:talk(0, "Tanna: Mari kita mulai.")
		elseif (opening == 106) then
			ichkin.side = 3
			ichkin:sendSide()
		elseif (opening == 107) then
			isgar.side = 1
			isgar:sendSide()
		elseif (opening == 111) then
			broadcast(mob.m, "")
			magnus:talk(3, "Magnus: *mulai memusatkan pikiran*")
		elseif (opening == 114) then
			isgar:talk(3, "Isgar: *mulai memusatkan pikiran*")
		elseif (opening == 116) then
			tanna:talk(3, "Tanna: *mulai memusatkan pikiran*")
		elseif (opening == 118) then
			hiro:talk(3, "Hiro: *mulai memusatkan pikiran*")
		elseif (opening == 119) then
			ichkin:talk(3, "Ichkin: *mulai memusatkan pikiran*")
		elseif (opening == 120) then
			arelai:talk(3, "Arelai: *mulai memusatkan pikiran*")
		elseif (opening == 127) then
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(2, "~mok ha emjata~")
		elseif (opening == 133) then
			magnus:sendAction(6, 255)
			isgar:sendAction(6, 255)
			tanna:sendAction(6, 255)
			hiro:sendAction(5, 255)
			ichkin:sendAction(5, 255)
			arelai:sendAction(5, 255)
		elseif (opening == 145) then
			magnus:sendAction(5, 255)
			isgar:sendAction(5, 255)
			tanna:sendAction(5, 255)
			hiro:sendAction(6, 255)
			ichkin:sendAction(6, 255)
			arelai:sendAction(6, 255)
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(2, "~mok ha emjata~")
		elseif (opening == 157) then
			magnus:sendAction(6, 80)
			isgar:sendAction(6, 80)
			tanna:sendAction(6, 80)
			hiro:sendAction(5, 80)
			ichkin:sendAction(5, 80)
			arelai:sendAction(5, 80)
		elseif (opening == 163) then
			magnus:sendAction(5, 80)
			isgar:sendAction(5, 80)
			tanna:sendAction(5, 80)
			hiro:sendAction(6, 80)
			ichkin:sendAction(6, 80)
			arelai:sendAction(6, 80)
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(2, "~mok ha emjata~")

			if (panda == nil) then
				mob:spawn(300007, 16, 18, 1)
				newMobPlayer = mob:getObjectsInSameMap(BL_MOB)

				for i = 1, #newMobPlayer do
					if (newMobPlayer[i].mobID == 300007) then
						newMobPlayer[i].side = 3
						newMobPlayer[i]:sendSide()
						mob.registry["opening_panda"] = newMobPlayer[i].ID
					end
				end
			end
		elseif (opening == 165) then
			panda:moveGhost()
		elseif (opening == 169) then
			magnus:sendAction(6, 80)
			isgar:sendAction(6, 80)
			tanna:sendAction(6, 80)
			hiro:sendAction(5, 80)
			ichkin:sendAction(5, 80)
			arelai:sendAction(5, 80)
			panda:moveGhost()
		elseif (opening == 172) then
			panda:moveGhost()
		elseif (opening == 175) then
			mob.light = 123
			player:refresh()
			player:changeView(6, 0)
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
			panda:moveGhost()
		elseif (opening == 177) then
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
		elseif (opening == 178) then
			panda:moveGhost()
		elseif (opening == 179) then
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(2, "~mok ha emjata~")
		elseif (opening == 181) then
			panda:moveGhost()
		elseif (opening == 184) then
			panda:moveGhost()
		elseif (opening == 185) then
			magnus:sendAction(6, 150)
			isgar:sendAction(6, 150)
			tanna:sendAction(6, 150)
			hiro:sendAction(6, 150)
			ichkin:sendAction(6, 150)
			arelai:sendAction(6, 150)
		elseif (opening == 187) then
			panda:moveGhost()
		elseif (opening == 189) then
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
			panda.side = 2
			panda:sendSide()
		elseif (opening == 191) then
			panda:moveGhost()
		elseif (opening == 193) then
			mob.light = 122
			player:refresh()
			player:changeView(6, 0)
		elseif (opening == 195) then
			magnus:sendAction(6, 80)
			isgar:sendAction(6, 80)
			tanna:sendAction(6, 80)
			hiro:sendAction(6, 80)
			ichkin:sendAction(6, 80)
			arelai:sendAction(6, 80)
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(0, "Arelai: Oh, manis sekali!")
			panda:sendAction(6, 255)
		elseif (opening == 203) then
			arelai:talk(0, "Arelai: Tunggu... apa? PANDA!")
		elseif (opening == 204) then
			arelai:sendAction(17, 80)
		elseif (opening == 209) then
			isgar:talk(0, "Isgar: Apa!?")
		elseif (opening == 215) then
			ichkin:talk(0, "Ichkin: ...")
			magnus:talk(0, "Magnus: Siapa ini?")
			isgar:sendAction(18, 80)
		elseif (opening == 221) then
			mob.light = 123
			player:refresh()
			player:changeView(6, 0)
			hiro:talk(0, "Hiro: Ugh... selalu begini.")
			magnus:sendAction(21, 80)
		elseif (opening == 227) then
			mob.light = 0
			player:refresh()
			player:changeView(6, 0)
			tanna:talk(0, "Tanna: Pergi. SEKARANG!")
			hiro:sendAction(23, 80)
		elseif (opening == 228) then
			panda:sendAction(12, 80)
		elseif (opening == 231) then
			panda.side = 0
			panda:sendSide()
		elseif (opening == 234) then
			panda:moveGhost()
		elseif (opening == 236) then
			panda.side = 3
			panda:sendSide()
		elseif (opening == 238) then
			panda:moveGhost()
		elseif (opening == 241) then
			panda:moveGhost()
		elseif (opening == 244) then
			panda:moveGhost()
		elseif (opening == 246) then
			isgar:talk(0, "Isgar: Berani sekali.")
		elseif (opening == 247) then
			panda:moveGhost()
		elseif (opening == 250) then
			panda:moveGhost()
		elseif (opening == 253) then
			panda:moveGhost()
		elseif (opening == 256) then
			panda:moveGhost()
		elseif (opening == 259) then
			panda:moveGhost()
			panda:sendAction(12, 80)
		elseif (opening == 262) then
			panda:removeHealth(panda.health)
			mob.registry["opening_panda"] = 0
		elseif (opening == 264) then
			if (corthol == nil) then
				mob:spawn(300006, 16, 18, 1)
				newMobPlayer = mob:getObjectsInSameMap(BL_MOB)

				for i = 1, #newMobPlayer do
					if (newMobPlayer[i].mobID == 300006) then
						newMobPlayer[i].side = 3
						newMobPlayer[i]:sendSide()
						mob.registry["opening_corthol"] = newMobPlayer[i].ID
					end
				end
			end
		elseif (opening == 266) then
			corthol:moveGhost()
		elseif (opening == 268) then
			corthol:talk(
				0,
				"Corthol: Ada apa ini? Kenapa belum disiapkan!?"
			)
		elseif (opening == 269) then
			corthol:moveGhost()
		elseif (opening == 272) then
			corthol:moveGhost()
		elseif (opening == 275) then
			corthol:moveGhost()
		elseif (opening == 278) then
			tanna:talk(0, "Tanna: Gangguan kecil. Sudah beres sekarang.")
			corthol:moveGhost()
		elseif (opening == 281) then
			corthol:moveGhost()
		elseif (opening == 284) then
			corthol:moveGhost()
		elseif (opening == 287) then
			corthol:moveGhost()
		elseif (opening == 288) then
			tanna:talk(1, "Tanna! Again!")
		elseif (opening == 289) then
			corthol.side = 2
			corthol:sendSide()
		elseif (opening == 292) then
			magnus:talk(3, "Magnus: *mulai memusatkan pikiran*")
			corthol:move()
		elseif (opening == 295) then
			isgar:talk(3, "Isgar: *mulai memusatkan pikiran*")
		elseif (opening == 297) then
			tanna:talk(3, "Tanna: *mulai memusatkan pikiran*")
		elseif (opening == 299) then
			hiro:talk(3, "Hiro: *mulai memusatkan pikiran*")
		elseif (opening == 300) then
			ichkin:talk(3, "Ichkin: *mulai memusatkan pikiran*")
		elseif (opening == 301) then
			arelai:talk(3, "Arelai: *mulai memusatkan pikiran*")
		elseif (opening == 308) then
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(2, "~mok ha emjata~")
		elseif (opening == 314) then
			magnus:sendAction(6, 255)
			isgar:sendAction(6, 255)
			tanna:sendAction(6, 255)
			hiro:sendAction(5, 255)
			ichkin:sendAction(5, 255)
			arelai:sendAction(5, 255)
		elseif (opening == 326) then
			magnus:sendAction(5, 255)
			isgar:sendAction(5, 255)
			tanna:sendAction(5, 255)
			hiro:sendAction(6, 255)
			ichkin:sendAction(6, 255)
			arelai:sendAction(6, 255)
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(2, "~mok ha emjata~")
		elseif (opening == 338) then
			magnus:sendAction(6, 80)
			isgar:sendAction(6, 80)
			tanna:sendAction(6, 80)
			hiro:sendAction(5, 80)
			ichkin:sendAction(5, 80)
			arelai:sendAction(5, 80)
		elseif (opening == 344) then
			magnus:sendAction(5, 80)
			isgar:sendAction(5, 80)
			tanna:sendAction(5, 80)
			hiro:sendAction(6, 80)
			ichkin:sendAction(6, 80)
			arelai:sendAction(6, 80)
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(2, "~mok ha emjata~")
		elseif (opening == 350) then
			magnus:sendAction(6, 80)
			isgar:sendAction(6, 80)
			tanna:sendAction(6, 80)
			hiro:sendAction(5, 80)
			ichkin:sendAction(5, 80)
			arelai:sendAction(5, 80)
		elseif (opening == 356) then
			mob.light = 123
			player:refresh()
			player:changeView(6, 0)
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
		elseif (opening == 358) then
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
		elseif (opening == 360) then
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(2, "~mok ha emjata~")
		elseif (opening == 366) then
			magnus:sendAction(6, 150)
			isgar:sendAction(6, 150)
			tanna:sendAction(6, 150)
			hiro:sendAction(6, 150)
			ichkin:sendAction(6, 150)
			arelai:sendAction(6, 150)
		elseif (opening == 370) then
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
		elseif (opening == 374) then
			mob.light = 122
			player:refresh()
			player:changeView(6, 0)
		elseif (opening == 376) then
			magnus:sendAction(6, 80)
			isgar:sendAction(6, 80)
			tanna:sendAction(6, 80)
			hiro:sendAction(6, 80)
			ichkin:sendAction(6, 80)
			arelai:sendAction(6, 80)
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(2, "~mok ha emjata~")
		elseif (opening == 380) then
			magnus:sendAction(5, 200)
			isgar:sendAction(5, 200)
			tanna:sendAction(5, 200)
			hiro:sendAction(6, 200)
			ichkin:sendAction(6, 200)
			arelai:sendAction(6, 200)
		elseif (opening == 389) then
			magnus:sendAction(6, 160)
			isgar:sendAction(6, 160)
			tanna:sendAction(6, 160)
			hiro:sendAction(5, 160)
			ichkin:sendAction(5, 160)
			arelai:sendAction(5, 160)
		elseif (opening == 396) then
			magnus:sendAction(5, 180)
			isgar:sendAction(5, 180)
			tanna:sendAction(5, 180)
			hiro:sendAction(6, 180)
			ichkin:sendAction(6, 180)
			arelai:sendAction(6, 180)
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(2, "~mok ha emjata~")
		elseif (opening == 404) then
			magnus:sendAction(6, 120)
			isgar:sendAction(6, 120)
			tanna:sendAction(6, 120)
			hiro:sendAction(5, 120)
			ichkin:sendAction(5, 120)
			arelai:sendAction(5, 120)
		elseif (opening == 410) then
			magnus:sendAction(5, 140)
			isgar:sendAction(5, 140)
			tanna:sendAction(5, 140)
			hiro:sendAction(6, 140)
			ichkin:sendAction(6, 140)
			arelai:sendAction(6, 140)
		elseif (opening == 417) then
			mob.light = 121
			player:refresh()
			player:changeView(6, 0)
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
			magnus:talk(2, "~mok ha emjata~")
			isgar:talk(2, "~mok ha emjata~")
			tanna:talk(2, "~mok ha emjata~")
			hiro:talk(2, "~mok ha emjata~")
			ichkin:talk(2, "~mok ha emjata~")
			arelai:talk(2, "~mok ha emjata~")
		elseif (opening == 419) then
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
		elseif (opening == 423) then
			magnus:sendAction(6, 130)
			isgar:sendAction(6, 130)
			tanna:sendAction(6, 130)
			hiro:sendAction(5, 130)
			ichkin:sendAction(5, 130)
			arelai:sendAction(5, 130)
		elseif (opening == 429) then
			magnus:sendAction(5, 100)
			isgar:sendAction(5, 100)
			tanna:sendAction(5, 100)
			hiro:sendAction(6, 100)
			ichkin:sendAction(6, 100)
			arelai:sendAction(6, 100)
		elseif (opening == 434) then
			magnus:sendAction(6, 80)
			isgar:sendAction(6, 80)
			tanna:sendAction(6, 80)
			hiro:sendAction(6, 80)
			ichkin:sendAction(6, 80)
			arelai:sendAction(6, 80)
		elseif (opening == 436) then
			magnus:talk(2, "~ea nobokho senba~")
			isgar:talk(2, "~ea nobokho senba~")
			tanna:talk(2, "~ea nobokho senba~")
			hiro:talk(2, "~ea nobokho senba~")
			ichkin:talk(2, "~ea nobokho senba~")
			arelai:talk(2, "~ea nobokho senba~")
		elseif (opening == 438) then
			mob.light = 120
			player:refresh()
			player:changeView(6, 0)
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
		elseif (opening == 440) then
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
		elseif (opening == 444) then
			magnus:sendAction(8, 80)
			isgar:sendAction(8, 80)
			tanna:sendAction(8, 80)
			hiro:sendAction(8, 80)
			ichkin:sendAction(8, 80)
			arelai:sendAction(8, 80)
		elseif (opening == 448) then
			magnus:sendAction(6, 80)
			isgar:sendAction(6, 80)
			tanna:sendAction(6, 80)
			hiro:sendAction(6, 80)
			ichkin:sendAction(6, 80)
			arelai:sendAction(6, 80)
			magnus:talk(2, "~ea nobokho senba~")
			isgar:talk(2, "~ea nobokho senba~")
			tanna:talk(2, "~ea nobokho senba~")
			hiro:talk(2, "~ea nobokho senba~")
			ichkin:talk(2, "~ea nobokho senba~")
			arelai:talk(2, "~ea nobokho senba~")
		elseif (opening == 449) then
			corthol:talk(
				0,
				"Corthol: Cepat! Begitu ritual ini selesai, akan kukuasai kekuatan tanah-tanah ini!"
			)
		elseif (opening == 450) then
			setTile(mob.m, 12, 22, 35836)
		elseif (opening == 452) then
			setTile(mob.m, 8, 23, 35836)
		elseif (opening == 454) then
			setTile(mob.m, 4, 22, 35836)
		elseif (opening == 456) then
			setTile(mob.m, 4, 17, 35836)
		elseif (opening == 458) then
			setTile(mob.m, 8, 16, 35836)
		elseif (opening == 460) then
			setTile(mob.m, 12, 17, 35836)
		elseif (opening == 461) then
			mob.light = 119
			player:refresh()
			player:changeView(6, 0)
		elseif (opening == 463) then
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
		elseif (opening == 467) then
			magnus:sendAction(6, 80)
			isgar:sendAction(6, 80)
			tanna:sendAction(6, 80)
			hiro:sendAction(6, 80)
			ichkin:sendAction(6, 80)
			arelai:sendAction(6, 80)
		elseif (opening == 469) then
			corthol:talk(
				0,
				"Corthol: Ya, kurasakan diriku menyerap bumi di bawah kita."
			)
		elseif (opening == 471) then
			mob.light = 118
			player:refresh()
			player:changeView(6, 0)
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
		elseif (opening == 473) then
			setTile(mob.m, 12, 22, 35838)
			setTile(mob.m, 4, 22, 35838)
			setTile(mob.m, 8, 16, 35838)
			setTile(mob.m, 4, 17, 35838)
			setTile(mob.m, 12, 17, 35838)
			setTile(mob.m, 8, 23, 35838)
			magnus:talk(2, "~mok~")
			isgar:talk(2, "~mok~")
			tanna:talk(2, "~mok~")
			hiro:talk(2, "~mok~")
			ichkin:talk(2, "~mok~")
			arelai:talk(2, "~mok~")
		elseif (opening == 477) then
			setTile(mob.m, 12, 22, 35836)
			setTile(mob.m, 4, 22, 35836)
			setTile(mob.m, 8, 16, 35836)
			setTile(mob.m, 4, 17, 35836)
			setTile(mob.m, 12, 17, 35836)
			setTile(mob.m, 8, 23, 35836)
			magnus:sendAction(6, 80)
			isgar:sendAction(6, 80)
			tanna:sendAction(6, 80)
			hiro:sendAction(6, 80)
			ichkin:sendAction(6, 80)
			arelai:sendAction(6, 80)
			magnus:talk(2, "~ha~")
			isgar:talk(2, "~ha~")
			tanna:talk(2, "~ha~")
			hiro:talk(2, "~ha~")
			ichkin:talk(2, "~ha~")
			arelai:talk(2, "~ha~")
		elseif (opening == 481) then
			setTile(mob.m, 12, 22, 35838)
			setTile(mob.m, 4, 22, 35838)
			setTile(mob.m, 8, 16, 35838)
			setTile(mob.m, 4, 17, 35838)
			setTile(mob.m, 12, 17, 35838)
			setTile(mob.m, 8, 23, 35838)
			mob.light = 117
			player:refresh()
			player:changeView(6, 0)
			magnus:sendAction(6, 60)
			isgar:sendAction(6, 60)
			tanna:sendAction(6, 60)
			hiro:sendAction(6, 60)
			ichkin:sendAction(6, 60)
			arelai:sendAction(6, 60)
			magnus:talk(2, "~emjata~")
			isgar:talk(2, "~emjata~")
			tanna:talk(2, "~emjata~")
			hiro:talk(2, "~emjata~")
			ichkin:talk(2, "~emjata~")
			arelai:talk(2, "~emjata~")
		elseif (opening == 484) then
			setTile(mob.m, 12, 22, 35836)
			setTile(mob.m, 4, 22, 35836)
			setTile(mob.m, 8, 16, 35836)
			setTile(mob.m, 4, 17, 35836)
			setTile(mob.m, 12, 17, 35836)
			setTile(mob.m, 8, 23, 35836)
			magnus:sendAction(6, 60)
			isgar:sendAction(6, 60)
			tanna:sendAction(6, 60)
			hiro:sendAction(6, 60)
			ichkin:sendAction(6, 60)
			arelai:sendAction(6, 60)
		elseif (opening == 485) then
			magnus:talk(2, "~mok~")
			isgar:talk(2, "~mok~")
			tanna:talk(2, "~mok~")
			hiro:talk(2, "~mok~")
			ichkin:talk(2, "~mok~")
			arelai:talk(2, "~mok~")
		elseif (opening == 487) then
			setTile(mob.m, 12, 22, 35838)
			setTile(mob.m, 4, 22, 35838)
			setTile(mob.m, 8, 16, 35838)
			setTile(mob.m, 4, 17, 35838)
			setTile(mob.m, 12, 17, 35838)
			setTile(mob.m, 8, 23, 35838)
			magnus:sendAction(6, 40)
			isgar:sendAction(6, 40)
			tanna:sendAction(6, 40)
			hiro:sendAction(6, 40)
			ichkin:sendAction(6, 40)
			arelai:sendAction(6, 40)
			corthol:talk(
				0,
				"Corthol: Sebentar lagi kekuatanku setara dengan para dewa!"
			)
		elseif (opening == 489) then
			setTile(mob.m, 12, 22, 35836)
			setTile(mob.m, 4, 22, 35836)
			setTile(mob.m, 8, 16, 35836)
			setTile(mob.m, 4, 17, 35836)
			setTile(mob.m, 12, 17, 35836)
			setTile(mob.m, 8, 23, 35836)
			magnus:sendAction(6, 20)
			isgar:sendAction(6, 20)
			tanna:sendAction(6, 20)
			hiro:sendAction(6, 20)
			ichkin:sendAction(6, 20)
			arelai:sendAction(6, 20)
			magnus:talk(2, "~ha~")
			isgar:talk(2, "~ha~")
			tanna:talk(2, "~ha~")
			hiro:talk(2, "~ha~")
			ichkin:talk(2, "~ha~")
			arelai:talk(2, "~ha~")
		elseif (opening == 490) then
			setTile(mob.m, 12, 22, 35838)
			setTile(mob.m, 4, 22, 35838)
			setTile(mob.m, 8, 16, 35838)
			setTile(mob.m, 4, 17, 35838)
			setTile(mob.m, 12, 17, 35838)
			setTile(mob.m, 8, 23, 35838)
			mob.light = 116
			player:refresh()
			player:changeView(6, 0)
			magnus:sendAction(6, 20)
			isgar:sendAction(6, 20)
			tanna:sendAction(6, 20)
			hiro:sendAction(6, 20)
			ichkin:sendAction(6, 20)
			arelai:sendAction(6, 20)
		elseif (opening == 491) then
			setTile(mob.m, 12, 22, 35836)
			setTile(mob.m, 4, 22, 35836)
			setTile(mob.m, 8, 16, 35836)
			setTile(mob.m, 4, 17, 35836)
			setTile(mob.m, 12, 17, 35836)
			setTile(mob.m, 8, 23, 35836)
			magnus:sendAction(6, 20)
			isgar:sendAction(6, 20)
			tanna:sendAction(6, 20)
			hiro:sendAction(6, 20)
			ichkin:sendAction(6, 20)
			arelai:sendAction(6, 20)
		elseif (opening == 493) then
			magnus:talk(2, "~emjata~")
			isgar:talk(2, "~emjata~")
			tanna:talk(2, "~emjata~")
			hiro:talk(2, "~emjata~")
			ichkin:talk(2, "~emjata~")
			arelai:talk(2, "~emjata~")
		elseif (opening == 496) then
			mob.light = 116
			player:refresh()
			player:changeView(6, 0)
			magnus:sendAction(4, 80)
			isgar:sendAction(4, 80)
			tanna:sendAction(4, 80)
			hiro:sendAction(4, 80)
			ichkin:sendAction(4, 80)
			arelai:sendAction(4, 80)
		elseif (opening == 497) then
			magnus:talk(2, "~mok~")
			isgar:talk(2, "~mok~")
			tanna:talk(2, "~mok~")
			hiro:talk(2, "~mok~")
			ichkin:talk(2, "~mok~")
			arelai:talk(2, "~mok~")
		elseif (opening == 501) then
			magnus:talk(2, "~ha~")
			isgar:talk(2, "~ha~")
			tanna:talk(2, "~ha~")
			hiro:talk(2, "~ha~")
			ichkin:talk(2, "~ha~")
			arelai:talk(2, "~ha~")
		elseif (opening == 505) then
			magnus:sendAnimation(135, 8)
			isgar:sendAnimation(135, 8)
			tanna:sendAnimation(135, 9)
			hiro:sendAnimation(135, 8)
			ichkin:sendAnimation(135, 8)
			arelai:sendAnimation(135, 8)
			magnus:talk(2, "~emjata~")
			isgar:talk(2, "~emjata~")
			tanna:talk(2, "~emjata~")
			hiro:talk(2, "~emjata~")
			ichkin:talk(2, "~emjata~")
			arelai:talk(2, "~emjata~")
		elseif (opening == 509) then
			magnus:talk(2, "~mok~")
			isgar:talk(2, "~mok~")
			tanna:talk(2, "~mok~")
			hiro:talk(2, "~mok~")
			ichkin:talk(2, "~mok~")
			arelai:talk(2, "~mok~")
		elseif (opening == 513) then
			magnus:talk(2, "~ha~")
			isgar:talk(2, "~ha~")
			tanna:talk(2, "~ha~")
			hiro:talk(2, "~ha~")
			ichkin:talk(2, "~ha~")
			arelai:talk(2, "~ha~")
		elseif (opening == 517) then
			magnus:talk(2, "~emjata~")
			isgar:talk(2, "~emjata~")
			tanna:talk(2, "~emjata~")
			hiro:talk(2, "~emjata~")
			ichkin:talk(2, "~emjata~")
			arelai:talk(2, "~emjata~")
		elseif (opening == 533) then
			magnus:sendAnimationXY(135, 11, 21, 1)
			isgar:sendAnimationXY(135, 5, 21, 1)
			hiro:sendAnimationXY(135, 5, 18, 1)
			ichkin:sendAnimationXY(135, 11, 18, 1)
			arelai:sendAnimationXY(135, 8, 22, 1)
		elseif (opening == 535) then
			setMap(mob.m, "../mithiamaps/Accepted/region1/r1_clifftop2.map")
			setTile(mob.m, 12, 22, 35836)
			setTile(mob.m, 4, 22, 35836)
			setTile(mob.m, 8, 16, 35836)
			setTile(mob.m, 4, 17, 35836)
			setTile(mob.m, 12, 17, 35836)
			setTile(mob.m, 8, 23, 35836)
			magnus:sendAnimationXY(135, 10, 20, 1)
			isgar:sendAnimationXY(135, 6, 20, 1)
			tanna:sendAnimationXY(135, 8, 17, 1)
			hiro:sendAnimationXY(135, 6, 18, 1)
			ichkin:sendAnimationXY(135, 10, 18, 1)
			arelai:sendAnimationXY(135, 8, 21, 1)
		elseif (opening == 537) then
			magnus:sendAnimationXY(135, 9, 19, 1)
			isgar:sendAnimationXY(135, 7, 19, 1)
			tanna:sendAnimationXY(135, 8, 18, 1)
			arelai:sendAnimationXY(135, 8, 20, 1)
		elseif (opening == 539) then
			mob.light = 115
			player:refresh()
			player:changeView(6, 0)
			magnus:sendAnimationXY(135, 8, 19)
		elseif (opening == 543) then
			setMap(mob.m, "../mithiamaps/Accepted/region1/r1_clifftop.map")
			setTile(mob.m, 12, 22, 35836)
			setTile(mob.m, 4, 22, 35836)
			setTile(mob.m, 8, 16, 35836)
			setTile(mob.m, 4, 17, 35836)
			setTile(mob.m, 12, 17, 35836)
			setTile(mob.m, 8, 23, 35836)
			magnus:sendAnimation(135, 1)
			isgar:sendAnimation(135, 1)
			tanna:sendAnimation(135, 2)
			hiro:sendAnimation(135, 1)
			ichkin:sendAnimation(135, 1)
			arelai:sendAnimation(135, 1)
		elseif (opening == 545) then
			magnus:sendAnimationXY(135, 11, 21, 1)
			isgar:sendAnimationXY(135, 5, 21, 1)
			hiro:sendAnimationXY(135, 5, 18, 1)
			ichkin:sendAnimationXY(135, 11, 18, 1)
			arelai:sendAnimationXY(135, 8, 22, 1)
		elseif (opening == 547) then
			setMap(mob.m, "../mithiamaps/Accepted/region1/r1_clifftop2.map")
			setTile(mob.m, 12, 22, 35836)
			setTile(mob.m, 4, 22, 35836)
			setTile(mob.m, 8, 16, 35836)
			setTile(mob.m, 4, 17, 35836)
			setTile(mob.m, 12, 17, 35836)
			setTile(mob.m, 8, 23, 35836)
			magnus:sendAnimationXY(135, 10, 20, 1)
			isgar:sendAnimationXY(135, 6, 20, 1)
			tanna:sendAnimationXY(135, 8, 17, 1)
			hiro:sendAnimationXY(135, 6, 18, 1)
			ichkin:sendAnimationXY(135, 10, 18, 1)
			arelai:sendAnimationXY(135, 8, 21, 1)
		elseif (opening == 549) then
			magnus:sendAnimationXY(135, 9, 19, 1)
			isgar:sendAnimationXY(135, 7, 19, 1)
			tanna:sendAnimationXY(135, 8, 18, 1)
			arelai:sendAnimationXY(135, 8, 20, 1)
		elseif (opening == 551) then
			setMap(mob.m, "../mithiamaps/Accepted/region1/r1_clifftop3.map")
			setTile(mob.m, 12, 22, 35836)
			setTile(mob.m, 4, 22, 35836)
			setTile(mob.m, 8, 16, 35836)
			setTile(mob.m, 4, 17, 35836)
			setTile(mob.m, 12, 17, 35836)
			setTile(mob.m, 8, 23, 35836)
			mob.light = 114
			player:refresh()
			player:changeView(6, 0)
			magnus:sendAnimationXY(135, 8, 19)
		elseif (opening == 555) then
			setMap(mob.m, "../mithiamaps/Accepted/region1/r1_clifftop2.map")
			setTile(mob.m, 12, 22, 35840)
			setTile(mob.m, 4, 22, 35840)
			setTile(mob.m, 8, 16, 35840)
			setTile(mob.m, 4, 17, 35840)
			setTile(mob.m, 12, 17, 35840)
			setTile(mob.m, 8, 23, 35840)
			magnus:sendAnimation(135, 1)
			isgar:sendAnimation(135, 1)
			tanna:sendAnimation(135, 2)
			hiro:sendAnimation(135, 1)
			ichkin:sendAnimation(135, 1)
			arelai:sendAnimation(135, 1)
		elseif (opening == 557) then
			magnus:sendAnimationXY(135, 11, 21, 1)
			isgar:sendAnimationXY(135, 5, 21, 1)
			hiro:sendAnimationXY(135, 5, 18, 1)
			ichkin:sendAnimationXY(135, 11, 18, 1)
			arelai:sendAnimationXY(135, 8, 22, 1)
		elseif (opening == 559) then
			setMap(mob.m, "../mithiamaps/Accepted/region1/r1_clifftop3.map")
			setTile(mob.m, 12, 22, 35840)
			setTile(mob.m, 4, 22, 35840)
			setTile(mob.m, 8, 16, 35840)
			setTile(mob.m, 4, 17, 35840)
			setTile(mob.m, 12, 17, 35840)
			setTile(mob.m, 8, 23, 35840)
			magnus:sendAnimationXY(135, 10, 20, 1)
			isgar:sendAnimationXY(135, 6, 20, 1)
			tanna:sendAnimationXY(135, 8, 17, 1)
			hiro:sendAnimationXY(135, 6, 18, 1)
			ichkin:sendAnimationXY(135, 10, 18, 1)
			arelai:sendAnimationXY(135, 8, 21, 1)
		elseif (opening == 561) then
			magnus:sendAnimationXY(135, 9, 19, 1)
			isgar:sendAnimationXY(135, 7, 19, 1)
			tanna:sendAnimationXY(135, 8, 18, 1)
			hiro:sendAnimationXY(135, 9, 19, 1)
			ichkin:sendAnimationXY(135, 7, 19, 1)
			arelai:sendAnimationXY(135, 8, 20, 1)
		elseif (opening == 563) then
			setMap(mob.m, "../mithiamaps/Accepted/region1/r1_clifftop4.map")
			mob.light = 113
			player:refresh()
			player:changeView(6, 0)
			magnus:sendAnimationXY(135, 8, 19, 80)
			isgar:sendAnimationXY(135, 8, 19, 80)
			tanna:sendAnimationXY(135, 8, 19, 80)
			hiro:sendAnimationXY(135, 8, 19, 80)
			ichkin:sendAnimationXY(135, 8, 19, 80)
			arelai:sendAnimationXY(135, 8, 19, 80)
		elseif (opening == 568) then
			player:unlock()
			async(opening_scene_controller.decision_one(player, mob))
		elseif (opening == 569) then
			if (mob.registry["opening_counter"] >= 240) then
				mob.registry["opening_decision_one"] = 1
			else
				mob.registry["opening_counter"] = mob.registry[
					"opening_counter"
				] + 1
				mob.registry["opening"] = 568
			end
		elseif (opening == 570) then
			if (decision == 1) then
				magnus:talk(2, "~mok ha emjata~")
				isgar:talk(2, "~mok ha emjata~")
				tanna:talk(2, "~mok ha emjata~")
				hiro:talk(2, "~mok ha emjata~")
				ichkin:talk(2, "~mok ha emjata~")
				arelai:talk(2, "~mok ha emjata~")
			elseif (decision == 2) then
				mob.side = 1
				mob:sendSide()
			else
				mob:talk(3, player.name .. ": Ada apa ini?")
			end
		elseif (opening == 572) then
			if (decision == 2) then
				mob:moveGhost()
			end
		elseif (opening == 574) then
			if (decision == 2) then
				mob.side = 0
				mob:sendSide()
			end
		elseif (opening == 576) then
			if (decision == 2) then
				mob:moveGhost()
			end
		elseif (opening == 579) then
			if (decision == 2) then
				mob:moveGhost()
			end
		elseif (opening == 583) then
			if (decision == 1 or decision == 3) then
				magnus:talk(2, "~mok~")
				isgar:talk(2, "~mok~")
				tanna:talk(2, "~mok~")
				hiro:talk(2, "~mok~")
				ichkin:talk(2, "~mok~")
				arelai:talk(2, "~mok~")
			else
				mob:talk(
					0,
					player.name .. ": Apa kabar, kawan-kawan? Boleh aku ikut?"
				)
			end
		elseif (opening == 587) then
			if (decision == 1 or decision == 3) then
				magnus:talk(2, "~ha~")
				isgar:talk(2, "~ha~")
				tanna:talk(2, "~ha~")
				hiro:talk(2, "~ha~")
				ichkin:talk(2, "~ha")
				arelai:talk(2, "~ha~")
			end
		elseif (opening == 593) then
			if (decision == 1 or decision == 3) then
				magnus:talk(2, "~emjata~")
				isgar:talk(2, "~emjata~")
				tanna:talk(2, "~emjata~")
				hiro:talk(2, "~emjata~")
				ichkin:talk(2, "~emjata~")
				arelai:talk(2, "~emjata~")
			else
				mob.light = 0
				player:refresh()
				player:changeView(6, 0)
				corthol:talk(0, "Corthol: Kau bercanda, kan? Lihat dirimu.")
			end
		elseif (opening == 601) then
			if (decision == 1 or decision == 3) then
				magnus:talk(2, "~ea~")
				isgar:talk(2, "~ea~")
				tanna:talk(2, "~ea~")
				hiro:talk(2, "~ea~")
				ichkin:talk(2, "~ea")
				arelai:talk(2, "~ea~")
			end
		elseif (opening == 603) then
			if (decision == 2) then
				corthol:talk(1, "Corthol! Habisi mereka!")
			end
		elseif (opening == 605) then
			if (decision == 1 or decision == 3) then
				magnus:talk(2, "~nobokho~")
				isgar:talk(2, "~nobokho~")
				tanna:talk(2, "~nobokho~")
				hiro:talk(2, "~nobokho~")
				ichkin:talk(2, "~nobokho~")
				arelai:talk(2, "~nobokho~")
				magnus:sendAnimation(135, 1)
				isgar:sendAnimation(135, 1)
				tanna:sendAnimation(135, 2)
				hiro:sendAnimation(135, 1)
				ichkin:sendAnimation(135, 1)
				arelai:sendAnimation(135, 1)
			end
		elseif (opening == 607) then
			if (decision == 1 or decision == 3) then
				magnus:sendAnimationXY(135, 11, 21, 1)
				isgar:sendAnimationXY(135, 5, 21, 1)
				hiro:sendAnimationXY(135, 5, 18, 1)
				ichkin:sendAnimationXY(135, 11, 18, 1)
				arelai:sendAnimationXY(135, 8, 22, 1)
			end
		elseif (opening == 609) then
			if (decision == 1 or decision == 3) then
				magnus:sendAnimationXY(135, 10, 20, 1)
				isgar:sendAnimationXY(135, 6, 20, 1)
				tanna:sendAnimationXY(135, 8, 17, 1)
				hiro:sendAnimationXY(135, 6, 18, 1)
				ichkin:sendAnimationXY(135, 10, 18, 1)
				arelai:sendAnimationXY(135, 8, 21, 1)
			else
				if (attacker == 2) then
					isgar:warp(mob.m, mob.x - 1, mob.y)
				elseif (attacker == 5) then
					ichkin:warp(mob.m, mob.x + 1, mob.y)
				end
			end
		elseif (opening == 611) then
			if (decision == 1 or decision == 3) then
				magnus:sendAnimationXY(135, 9, 19, 1)
				isgar:sendAnimationXY(135, 7, 19, 1)
				tanna:sendAnimationXY(135, 8, 18, 1)
				hiro:sendAnimationXY(135, 9, 19, 1)
				ichkin:sendAnimationXY(135, 7, 19, 1)
				arelai:sendAnimationXY(135, 8, 20, 1)
			end
		elseif (opening == 613) then
			if (decision == 1 or decision == 3) then
				mob.light = 112
				player:refresh()
				player:changeView(6, 0)
				magnus:talk(2, "~senba~")
				isgar:talk(2, "~senba~")
				tanna:talk(2, "~senba~")
				hiro:talk(2, "~senba~")
				ichkin:talk(2, "~senba~")
				arelai:talk(2, "~senba~")
				magnus:sendAnimationXY(135, 8, 19, 80)
				isgar:sendAnimationXY(135, 8, 19, 80)
				tanna:sendAnimationXY(135, 8, 19, 80)
				hiro:sendAnimationXY(135, 8, 19, 80)
				ichkin:sendAnimationXY(135, 8, 19, 80)
				arelai:sendAnimationXY(135, 8, 19, 80)
			else
				if (attacker == 1) then
					magnus:sendAction(6, 80)
					mob:selfAnimation(player.ID, 333, 0)
				elseif (attacker == 2) then
					isgar:sendAction(1, 80)
					mob:selfAnimation(player.ID, 85, 0)
				elseif (attacker == 3) then
					tanna:sendAction(6, 80)
					mob:selfAnimation(player.ID, 422, 0)
				elseif (attacker == 4) then
					hiro:sendAction(6, 80)
					mob:selfAnimation(player.ID, 275, 0)
				elseif (attacker == 5) then
					ichkin:sendAction(1, 80)
					mob:selfAnimation(player.ID, 128, 0)
				elseif (attacker == 6) then
					arelai:sendAction(6, 80)
					mob:selfAnimation(player.ID, 322, 0)
				end

				--corthol:sendAction(6, 80)
				--player:selfAnimation(corthol, 0)
			end
		elseif (opening == 617) then
			if (decision == 2) then
				corthol:talk(0, "Corthol: Silly peasants.")
				corthol:sendAction(11, 80)
				player.blind = true
			end
		elseif (opening == 618) then
			if (decision == 1 or decision == 3) then
				corthol:talk(
					0,
					"Corthol: Cukup! Akan kutunjukkan kepada dunia ini arti kekuatan yang sebenarnya."
				)
			end
		elseif (opening == 623) then
			if (decision == 2) then
				mob.registry["opening"] = 799
			end
		elseif (opening == 630) then
			corthol:talk(
				0,
				"Corthol: Rumah dan desa mereka yang tidak menyembahku akan dimusnahkan."
			)
		elseif (opening == 637) then
			mob.light = 111
			player:refresh()
			player:changeView(6, 0)
		elseif (opening == 642) then
			magnus:sendAnimation(393, 1)
			isgar:sendAnimation(393, 1)
			tanna:sendAnimation(393, 1)
			hiro:sendAnimation(393, 1)
			ichkin:sendAnimation(393, 1)
			arelai:sendAnimation(393, 1)
			corthol:talk(
				0,
				"Corthol: Aku, Corthol, kini telah naik dan menyatu dengan para Dewa."
			)
		elseif (opening == 644) then
			if (decision == 3) then
				mob:warp(mob.m, arelai.x, arelai.y)
				mob:talk(2, "~lunges~")
			end
		elseif (opening == 646) then
			mob.registry["opening_magnus"] = 0
			mob.registry["opening_isgar"] = 0
			mob.registry["opening_tanna"] = 0
			mob.registry["opening_hiro"] = 0
			mob.registry["opening_ichkin"] = 0
			mob.registry["opening_arelai"] = 0
			magnus:removeHealth(magnus.health)
			isgar:removeHealth(isgar.health)
			tanna:removeHealth(tanna.health)
			hiro:removeHealth(hiro.health)
			ichkin:removeHealth(ichkin.health)
			arelai:removeHealth(arelai.health)

			if (decision == 1) then
				mob.side = 1
				mob:sendSide()
			else
				player.blind = true
			end
		elseif (opening == 648) then
			if (decision == 1) then
				corthol.side = 0
				corthol:sendSide()
				mob:moveGhost()
			end
		elseif (opening == 650) then
			if (decision == 1) then
				corthol:moveGhost()
				mob.side = 0
				mob:sendSide()
			end
		elseif (opening == 652) then
			if (decision == 1) then
				mob:moveGhost()
			else
				corthol:talk(0, "Corthol: Hah? Apa itu tadi?")
			end
		elseif (opening == 653) then
			if (decision == 1) then
				corthol:moveGhost()
			end
		elseif (opening == 655) then
			if (decision == 1) then
				mob:moveGhost()
			end
		elseif (opening == 656) then
			if (decision == 1) then
				corthol:moveGhost()
			end
		elseif (opening == 658) then
			if (decision == 1) then
				mob:moveGhost()
			else
				mob.registry["opening"] = 799
			end
		elseif (opening == 659) then
			corthol:moveGhost()
		elseif (opening == 661) then
			mob:moveGhost()
		elseif (opening == 662) then
			corthol:talk(
				0,
				"Corthol: Para dewa bilang aku tidak layak menjadi salah satu dari mereka. Akan kubuktikan."
			)
			corthol:moveGhost()
		elseif (opening == 664) then
			mob:moveGhost()
		elseif (opening == 665) then
			corthol:moveGhost()
		elseif (opening == 667) then
			mob:moveGhost()
		elseif (opening == 668) then
			corthol:moveGhost()
		elseif (opening == 670) then
			mob:moveGhost()
		elseif (opening == 674) then
			player:changeView(6, 7)
			corthol:talk(
				0,
				"Corthol: Mereka pikir hanya merekalah yang boleh mahakuasa."
			)
			corthol:moveGhost()
		elseif (opening == 676) then
			corthol.side = 1
			corthol:sendSide()
			mob:moveGhost()
		elseif (opening == 678) then
			corthol:moveGhost()
		elseif (opening == 679) then
			mob:moveGhost()
		elseif (opening == 680) then
			corthol.side = 0
			corthol:sendSide()
		elseif (opening == 682) then
			corthol:moveGhost()
			mob:moveGhost()
		elseif (opening == 686) then
			corthol:talk(
				0,
				"Corthol: Hanya karena garis darah mereka menentukannya, cih!"
			)
			corthol.side = 1
			corthol:sendSide()
		elseif (opening == 687) then
			mob:moveGhost()
		elseif (opening == 688) then
			corthol:moveGhost()
		elseif (opening == 689) then
			mob.side = 1
			mob:sendSide()
		elseif (opening == 690) then
			corthol.side = 0
			corthol:sendSide()
		elseif (opening == 691) then
			mob:moveGhost()
		elseif (opening == 692) then
			corthol:moveGhost()
		elseif (opening == 694) then
			mob:moveGhost()
		elseif (opening == 698) then
			corthol:talk(
				0,
				"Corthol: Akan kubawa teror ke tanah ini, lalu kucekik leher mereka."
			)
			mob.side = 0
			mob:sendSide()
		elseif (opening == 706) then
			mob:talk(
				3,
				player.name .. ": Sialan! Aku tidak boleh membiarkan ini terjadi, tidak boleh."
			)
		elseif (opening == 714) then
			player:changeView(6, 14)
			mob:talk(
				0,
				player.name .. ": Aku tidak akan membiarkanmu melakukan ini, tidak kepada tanah ini, tidak kepada orang-orang yang hidup damai di sini!"
			)
		elseif (opening == 718) then
			corthol.side = 2
			corthol:sendSide()
		elseif (opening == 730) then
			corthol:talk(
				0,
				"Corthol: Siapa kau? Berani-beraninya kau berpikir menggagalkan rencanaku!"
			)
		elseif (opening == 746) then
			corthol:talk(
				0,
				"Corthol: Menghalangi dewa dari tujuannya sama saja mengharapkan kematian!"
			)
		elseif (opening == 762) then
			corthol:talk(0, "Corthol: Akan kukabulkan permintaan itu.")
		elseif (opening == 770) then
			corthol:talk(0, "Corthol: Ha! Bodoh sekali.")
		elseif (opening == 773) then
			corthol:sendAction(6, 60)
		elseif (opening == 774) then
			mob:sendAnimation(161, 0)
		elseif (opening == 776) then
			corthol.side = 0
			corthol:sendSide()
		elseif (opening == 777) then
			mob:sendAnimation(252, 0)
		elseif (opening == 778) then
			corthol:moveGhost()
		elseif (opening == 779) then
			player.blind = true
		elseif (opening == 780) then
			corthol:sendAction(11, 80)
			mob.registry["opening"] = 799
		elseif (opening == 800) then
			mob.registry["opening_magnus"] = 0
			mob.registry["opening_isgar"] = 0
			mob.registry["opening_tanna"] = 0
			mob.registry["opening_hiro"] = 0
			mob.registry["opening_ichkin"] = 0
			mob.registry["opening_arelai"] = 0
			mob.registry["opening_panda"] = 0
			mob.registry["opening_corthol"] = 0
			mob.registry["opening_player"] = 0
			mob.registry["opening_attacker"] = 0
			mob.registry["opening_counter"] = 0
			mob.registry["opening_decision_one"] = 0
			setMap(mob.m, "../mithiamaps/Accepted/region1/r1_clifftop.map")
			table.remove(openingSceneUsedMaps, (mob.m - 20000 + 1))
			player:warp(1, xWarps[warp], yWarps[warp])
			player.state = 0
			player.gfxClone = 0
			player:updateState()
			player.blind = false
			player.clan = 1
			player:unlock()
			newMobPlayer = mob:getObjectsInSameMap(BL_MOB)

			for i = #newMobPlayer, 1, -1 do
				if (newMobPlayer[i].ID ~= mob.ID) then
					newMobPlayer[i]:removeHealth(newMobPlayer[i].health)
				end
			end
		end

		if (runScript) then
			--mob:talk(2, ""..mob.registry["opening"])
			opening_scene_controller.debugger = 0
			mob.registry["opening"] = mob.registry["opening"] + 1
		else
			mob:talk(2, "Penelusuran galat sedang berjalan.")
		end
	end,

	attack = function(mob, target)
	end,

	decision_one = async(function(player, mob)
		local decisionOne = player:menuString(
			"Ada apa ini? Haruskah aku berbuat sesuatu?",
			{"Wait.", "Assist.", "Attack."}
		)
		local gfx = {graphic = convertGraphic(1035, "monster"), color = 0}

		player.npcGraphic = gfx.graphic
		player.npcColor = gfx.color
		player.lastClick = F1_NPC

		if (decisionOne == "Wait.") then
			player:dialogSeq(
				{
					gfx,
					"Aku akan menunggu dan melihat apa yang terjadi untuk menilai keadaan lebih baik."
				},
				1
			)
			mob.registry["opening_decision_one"] = 1
			mob.registry["opening"] = 570
			player:lock()
		elseif (decisionOne == "Assist.") then
			player:dialogSeq(
				{gfx, "Aku akan bergabung dengannya dalam perebutan kekuatan!"},
				1
			)
			mob.registry["opening_decision_one"] = 2
			mob.registry["opening"] = 570
			player:lock()
		elseif (decisionOne == "Attack.") then
			player:dialogSeq({gfx, "Aku tidak bisa membiarkan mereka melakukan ini!"}, 1)
			mob.registry["opening_decision_one"] = 3
			mob.registry["opening"] = 570
			player:lock()
		else
			mob.registry["opening"] = 567
			player:lock()
		end
	end),
}

opening_scene = {
	maxmaps = 20,

	getfreemap = function()
		for i = 0, opening_scene.maxmaps - 1 do
			if ((openingSceneUsedMaps[i + 1] ~= nil and openingSceneUsedMaps[i + 1] ~= (20000 + i)) or openingSceneUsedMaps[
				i + 1
			] == nil) then
				return (i + 20000)
			end
		end

		return nil
	end,

	addqueue = function(id)
		for i = 0, #openingSceneQueue do
			if (openingSceneQueue[i] == id) then
				return
			end
		end

		table.insert(openingSceneQueue, id)
	end,

	startscene = function(player, map)
		local playerMob

		if (#openingSceneQueue > 0) then
			table.remove(openingSceneQueue, 1)
		end

		table.insert(openingSceneUsedMaps, (map - 20000 + 1), map)
		setMap(map, "../mithiamaps/Accepted/region1/r1_clifftop.map")
		player:lock()
		player:warp(map, 6, 12)
		player.mapTitle = "Opening Scene"

		--player.bgm = music
		player.light = 0
		player.weather = 2
		player.canTalk = 1
		player:spawn(300008, 0, 24, 1)
		playerMob = player:getObjectsInSameMap(BL_MOB)[1]
		opening_scene.setmoblooks(player, playerMob)
		player:refresh()
		player:changeView(6, 0)
		player.registry["login_count"] = 1
		player.accountRegistry["dark_wizard_opening"] = 1
	end,

	setmoblooks = function(player, playerMob)
		local maleArmor = {38, 36, 40, 34, 43}
		local femaleArmor = {42, 37, 41, 35, 39}

		if (player.sex == 0) then
			playerMob.gfxArmor = maleArmor[player.totem + 1]
		elseif (player.sex == 1) then
			playerMob.gfxArmor = femaleArmor[player.totem + 1]
		else
			player:talk(0, player.name .. ": Aku bukan manusia.")
		end

		playerMob.gfxWeap = 65535
		playerMob.gfxShield = 65535
		playerMob.gfxHelm = 65535
		playerMob.gfxFaceA = 65535
		playerMob.gfxFaceAT = 65535
		playerMob.gfxCrown = 65535
		playerMob.gfxCape = 65535
		playerMob.gfxNeck = 65535
		playerMob.gfxBoots = player.sex
		playerMob.gfxFace = player.face
		playerMob.gfxHair = player.hair
		playerMob.gfxHairC = player.hairColor
		playerMob.gfxFaceC = player.faceColor
		playerMob.gfxSkinC = player.skinColor
		playerMob.gfxDye = player.armorColor
		playerMob.gfxName = player.name
		playerMob.gfxClone = 1

		player.state = -1
		player.gfxArmor = player.sex
		player.gfxWeap = 65535
		player.gfxShield = 65535
		player.gfxHelm = 65535
		player.gfxFaceA = 65535
		player.gfxFaceAT = 65535
		player.gfxCrown = 65535
		player.gfxCape = 65535
		player.gfxNeck = 65535
		player.gfxBoots = player.sex
		player.gfxFace = player.face
		player.gfxHair = player.hair
		player.gfxHairC = player.hairColor
		player.gfxFaceC = player.faceColor
		player.gfxSkinC = player.skinColor
		player.gfxDye = player.armorColor
		player.gfxName = ""
		player.gfxClone = 1
		player:updateState()
	end,

	firstlogin = async(function(player)
		local spiritprogress = player.registry["spirit_progress"]
		local opts = {}
		table.insert(opts, "Ya")
		table.insert(opts, "Tidak")
		local seizures = {}
		table.insert(seizures, "Aku menderita kejang.")
		table.insert(seizures, "Aku tidak menderita kejang.")
		local menuOption, menuOption2
		local cont = 0
		local t = {graphic = convertGraphic(838, "monster"), color = 0}
		local e = {graphic = convertGraphic(1035, "monster"), color = 1}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.lastClick = F1_NPC
		player.blind = false
		player.state = -1
		player.gfxClone = 1
		player:updateState()

		if (player.registry["spirit_progress"] == 0) then
			if (player.registry["firstloginfail"] == 1) then
				--first login failed
				player:dialogSeq(
					{
						e,
						"Kau berusaha bangun tetapi tidak bisa; sepertinya kau harus menuntaskan mimpi ini..."
					},
					1
				)
			end

			player:dialogSeq(
				{
					e,
					"Tubuhmu terasa sangat ringan, dan saat matamu terbuka kau melihat sesuatu mendekat...",
					e,
					"...dengan pandangan yang masih kabur, kau seakan mengenali sebuah siluet...",
					t,
					"Greetings " .. player.name .. ". Aku roh Genesis, dan tugasku membantumu dalam perjalananmu.",
					t,
					"Aku akan setia kepadamu apa pun yang kau perbuat.",
					t,
					"Sebagian orang bilang takdir sudah tertulis, tetapi aku percaya meski tertulis, kau tetap bisa membuat pilihanmu sendiri untuk membentuk takdirmu.",
					t,
					"Aku percaya ada pilihan yang bahkan para dewa pun tidak bisa meramalkannya, sebab mereka tidak sanggup memahami kekuatan memilih.",
					t,
					"Sebagian pilihanmu akan memengaruhi cara dunia memandangmu. Pilihlah dengan bijak, sebab masa depanmu ada di tanganmu.",
					t,
					"Sekarang aku ingin kau membuat pilihan pertamamu. Sebagai wujud sepertiku, aku tidak diberi nama; mungkin kau bisa mengusulkan satu. Aku akan sering menghubungimu, jadi ini akan memudahkan kita berbicara."
				},
				1
			)
			player.registry["spirit_progress"] = 1
		end

		local f1n = ""
		local triggered = false

		if (player.registry["spirit_progress"] == 1) then
			player:dialogSeq({t, "Kau harus memberiku nama."}, 1)

			repeat
				if (triggered) then
					player:dialogSeq({t, "Namaku harus terdiri dari 4-16 huruf."}, 1)
					player.registry["spirit_progress"] = 1
				end

				f1n = player:input("Aku hendak dinamai apa?")
				triggered = true
			until (string.len(f1n) > 3 and string.len(f1n) <= 15)

			player.f1Name = f1n
			player.registry["spirit_progress"] = 2
		end

		if (player.registry["spirit_progress"] == 2) then
			player:dialogSeq(
				{
					t,
					"Mulai sekarang namaku " .. player.f1Name .. ".",
					t,
					"Kalau kau butuh bantuan, tekan tombol [F1] kapan saja untuk memanggilku."
				},
				1
			)
			player.registry["spirit_progress"] = 3
		end

		if (player.registry["spirit_progress"] == 3) then
			player:dialogSeq(
				{t, "Sekarang aku ingin tahu sedikit tentangmu."},
				1
			)
			if (#opts ~= 0) then
				menuOption = player:menu("Apakah kau menderita kejang?", opts)
				if (menuOption == 1) then
					--YES
					player.registry["settings_seizure"] = 1
					player:dialogSeq(
						{
							t,
							"-" .. player.f1Name .. " menuliskannya pada gulungan, dan begitu selesai gulungan itu lenyap-"
						},
						1
					)
					cont = 1
				elseif (menuOption == 2) then
					--NO
					menuOption2 = player:menu(
						"Kau yakin tidak menderita kejang?",
						seizures
					)
					if (menuOption2 == 1) then
						--I do suffer from seizures
						player.registry["settings_seizure"] = 1
						player:dialogSeq(
							{
								t,
								"-" .. player.f1Name .. " menuliskannya pada gulungan, dan begitu selesai gulungan itu lenyap-",
								t,
								"Aku turut prihatin; akan kuingat itu."
							},
							1
						)
						cont = 1
					elseif (menuOption2 == 2) then
						--I do not suffer from seizures
						player:dialogSeq(
							{
								t,
								"-" .. player.f1Name .. " menuliskannya pada gulungan, dan begitu selesai gulungan itu lenyap-",
								t,
								"Kalau kau ternyata menderita kejang dan salah menjawab, segera hubungi Game Master."
							},
							1
						)
						cont = 1
					end
				end
			end
		end
		if (cont == 1) then
			player.registry["spirit_progress"] = 4
		end

		if (player.registry["spirit_progress"] == 4) then
			player:dialogSeq(
				{
					t,
					"Sekarang aku ingin memberimu sedikit keterangan tentang dunia ini.",
					t,
					"Hal terpenting yang harus kau ingat: kau bertanggung jawab atas perbuatanmu sendiri.",
					t,
					"Setiap tindakan dan pilihanmu bisa memengaruhi cara dunia memandangmu.",
					t,
					"Sebagian tugas tidak punya akhir yang pasti, dan kaulah yang menentukan jalannya. Tidak seperti para dewa, kau punya kuasa membentuk takdirmu sendiri.",
					t,
					"Kedua, kau adalah pribadi yang bebas memilih bagaimana KAU berkembang, baik jasmani maupun batin.",
					t,
					"Kau sepenuhnya mengendalikan cara tubuhmu tumbuh.",
					t,
					"Seiring pengalaman yang kau kumpulkan, kau akan memperoleh pencerahan.",
					t,
					"Might membuat ayunan dan daya hidupmu lebih kuat, Will menajamkan ketepatan mantra serta memperbesar simpanan manamu, sedangkan Grace menambah ketepatanmu",
					t,
					"dan kelincahanmu menghindar, sambil memberi sedikit daya hidup maupun mana."
				},
				1
			)
			player.registry["spirit_progress"] = 5
		end
		if (player.registry["spirit_progress"] == 5) then
			player:dialogSeq(
				{
					t,
					"Sekarang akan kutelusuri pikiranmu untuk mencari apa yang membawamu kepadaku."
				},
				1
			)

			player.registry["spirit_progress"] = 6
		end

		--optional survey
		if (player.registry["spirit_progress"] == 6) then
			local map = opening_scene.getfreemap()

			if (map ~= nil) then
				opening_scene.startscene(player, map)
			else
				opening_scene.addqueue(player.ID)
			end
		end
	end)
}
