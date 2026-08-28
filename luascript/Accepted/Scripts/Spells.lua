local _getRequiredLevel = function(baseLevelRequired)
	local requiredLevel = math.ceil(baseLevelRequired * Config.learnSpellsFactor)
	return math.min(requiredLevel, Config.learnSpellsMaxLevel)
end

function Player.learnSpell(player, npc, additionalSpells)
	local t = {
		graphic = convertGraphic(npc.look, "monster"),
		color = npc.lookColor
	}

	player.npcGraphic = t.graphic
	player.npcColor = t.color
	player.dialogType = 0
	player.lastClick = npc.ID

	local spellYName = {}
	local spellName = {}
	local spellName2 = {}
	local spellYName2 = {}
	local unknownSpells = {}

	if (player.class >= 1 and player.class <= 4) or player.class >= 10 then
		-- regular path and pc subpaths (pc subpath spells will be available in pc subpath hall)
		unknownSpells = player:getUnknownSpells(player.baseClass)
	elseif player.class == 6 or player.class == 7 or player.class == 8 or player.class == 9 then
		-- NPC paths, returns baseclass + NPC subpath spells
		unknownSpells = player:getUnknownSpells(player.baseClass, player.class)
	end

	local spellLevelReq = {}
	local spellItemReq = {}
	local spellItemAmount = {}
	local spellDesc = {}
	local spellDisplay = {}

	for i = 1, #unknownSpells do
		if (i % 2 == 0) then
			table.insert(spellYName, unknownSpells[i])
		elseif (i % 2 == 1) then
			table.insert(spellName, unknownSpells[i])
		end
	end

	for i = 1, #spellYName do
		if spellYName[i] ~= "" then
			table.insert(spellYName2, spellYName[i])
			table.insert(spellName2, spellName[i])
		end
	end

	if additionalSpells ~= nil then
		for i = 1, #additionalSpells do
			table.insert(spellYName2, additionalSpells[i])
			local name = player:getSpellNameFromYName(additionalSpells[i])
			table.insert(spellName2, name)
		end
	end

	for i = 1, #spellYName2 do
		local level = 1
		local items = {}
		local amounts = {}
		local desc = {}
		local func = assert(loadstring("return " .. spellYName2[i] .. ".requirements"))(player)

		if (func ~= nil) then
			level, items, amounts, desc = func(player)
		end

		level = _getRequiredLevel(level)

		for j = 1, #items do
			if type(items[j]) == "string" then
				items[j] = Item(items[j]).id
			end
		end

		table.insert(spellLevelReq, level)
		table.insert(spellItemReq, items)
		table.insert(spellItemAmount, amounts)
		table.insert(spellDesc, desc)
		table.insert(
			spellDisplay,
			spellName2[i] .. " Lvl: " .. spellLevelReq[i]
		)
	end

	local sortedSpellName = sort_relative(spellLevelReq, spellName2)
	local sortedSpellYName = sort_relative(spellLevelReq, spellYName2)
	local sortedSpellItemReq = sort_relative(spellLevelReq, spellItemReq)
	local sortedSpellItemAmount = sort_relative(spellLevelReq, spellItemAmount)
	local sortedSpellDesc = sort_relative(spellLevelReq, spellDesc)
	local sortedSpellDisplay = sort_relative(spellLevelReq, spellDisplay)
	local sortedSpellLevelReq = sort_relative(spellLevelReq, spellLevelReq)

	--- Up to this point all spells ordered by level

	local i = 1

	while i <= #sortedSpellLevelReq do
		if (sortedSpellLevelReq[i] > player.level or player:hasSpell(sortedSpellYName[i])) then
			table.remove(sortedSpellItemReq, i)
			table.remove(sortedSpellItemAmount, i)
			table.remove(sortedSpellDesc, i)
			table.remove(sortedSpellName, i)
			table.remove(sortedSpellYName, i)
			table.remove(sortedSpellDisplay, i)
			table.remove(sortedSpellLevelReq, i)
			i = i - 1
		end
		
		i = i + 1
	end

	local choice = player:menuSeq(
		"Kau telah mengurai pikiranmu dan memperluas potensimu. Takdirmu sedang menunggu, bukan? Rahasia mana yang ingin kau pelajari?",
		sortedSpellName,
		{}
	)

	--player:talk(0,spellDesc[choice])
	local choice2 = player:menuSeq(
		"Kau siap mempelajari " .. sortedSpellName[choice] .. ": " .. sortedSpellDesc[
			choice
		] .. " Bersumpahkah kau memakai rahasia ini hanya untuk kebaikan?",
		{"Ya", "Tidak"},
		{}
	)

	-- TODO: Return here if choice2 == 2?

	local items = ""
	local itemName = ""
	local txt = ""

	if sortedSpellItemReq[choice] ~= nil and #sortedSpellItemReq[choice] ~= 0 then
		for i = 1, #sortedSpellItemReq[choice] do
			--player:talk(0,"id is: "..sortedSpellItemReq[choice][i])
			id = sortedSpellItemReq[choice][i]

			--player:talk(0,"amount is: "..sortedSpellItemAmount[choice][i])
			amount = sortedSpellItemAmount[choice][i]
			itemName = Item(id).name

			if (id == 0) then
				--Gold
				items = items .. amount .. " gold, "
			else
				items = items .. itemName .. " (" .. amount .. "), "
			end
		end
		txt = "The fee to learn " .. sortedSpellName[choice] .. " is:\n" .. items .. "All must be in good condition."
	elseif next(sortedSpellItemReq[choice]) == nil then
		txt = "The fee to learn " .. sortedSpellName[choice] .. " is: FREE\n"
	end

	local choice3 = player:menuString(txt, {"Ya", "Tidak"}, {})

	if choice3 == "Ya" then
		if sortedSpellItemReq[choice] ~= nil and #sortedSpellItemReq[choice] ~= 0 then
			for i = 1, #sortedSpellItemReq[choice] do
				id = sortedSpellItemReq[choice][i]
				amount = sortedSpellItemAmount[choice][i]

				if (id > 0) then
					if player:hasItem(id, amount) ~= true then
						player:menuString(
							"Membayar apa yang kau inginkan adalah tanda pengabdian. Kembalilah kalau syaratnya sudah kau penuhi.",
							{}
						)
						return
					end
				else
					if player.money < amount then
						player:menuString(
							"Membayar apa yang kau inginkan adalah tanda pengabdian. Kembalilah kalau syaratnya sudah kau penuhi.",
							{}
						)
						return
					end
				end
			end
		end

		-- Successful check of items and gold at this point.. proceed to taking the items and gold

		if sortedSpellItemReq[choice] ~= nil and #sortedSpellItemReq[choice] ~= 0 then
			for i = 1, #sortedSpellItemReq[choice] do
				id = sortedSpellItemReq[choice][i]
				amount = sortedSpellItemAmount[choice][i]

				if (id > 0) then
					player:removeItem(id, amount, 9)
				else
					player:removeGold(amount)
				end
			end
		end

		player:addSpell(sortedSpellYName[choice])
		player:sendMinitext("Pikiranmu meluas saat kau mempelajari " .. sortedSpellName[choice])
	elseif choice3 == "Tidak" then
		player:dialogSeq(
			{
				t,
				"Kemungkinan untuk belajar tidak ada habisnya; tetaplah rendah hati dan siap belajar."
			},
			1
		)
		return
	end
end

function Player.learnSpecificSpells(player, npc, spells)
	-- will be used for mainly PC subpath spell system

	local t = {
		graphic = convertGraphic(npc.look, "monster"),
		color = npc.lookColor
	}
	player.npcGraphic = t.graphic
	player.npcColor = t.color
	player.dialogType = 0
	player.lastClick = npc.ID

	if next(spells) == nil then
		return
	end

	local spellYName = {}
	local spellName = {}

	for i = 1, #spells do
		table.insert(spellYName, spells[i])
		table.insert(spellName, player:getSpellNameFromYName(spells[i]))
	end

	local spellLevelReq = {}
	local spellItemReq = {}
	local spellItemAmount = {}
	local spellDesc = {}
	local spellDisplay = {}

	for i = 1, #spellYName do
		local level = 1
		local items = {}
		local amounts = {}
		local desc = {}
		local func = assert(loadstring("return " .. spellYName[i] .. ".requirements"))(player)

		if (func ~= nil) then
			level, items, amounts, desc = func(player)
		end

		level = _getRequiredLevel(level)

		for j = 1, #items do
			if type(items[j]) == "string" then
				items[j] = Item(items[j]).id
			end
		end

		table.insert(spellLevelReq, level)
		table.insert(spellItemReq, items)
		table.insert(spellItemAmount, amounts)
		table.insert(spellDesc, desc)
		table.insert(spellDisplay, spellName[i] .. " Lvl: " .. spellLevelReq[i])
	end

	local sortedSpellName = sort_relative(spellLevelReq, spellName)
	local sortedSpellYName = sort_relative(spellLevelReq, spellYName)
	local sortedSpellItemReq = sort_relative(spellLevelReq, spellItemReq)
	local sortedSpellItemAmount = sort_relative(spellLevelReq, spellItemAmount)
	local sortedSpellDesc = sort_relative(spellLevelReq, spellDesc)
	local sortedSpellDisplay = sort_relative(spellLevelReq, spellDisplay)
	local sortedSpellLevelReq = sort_relative(spellLevelReq, spellLevelReq)

	--- Up to this point all spells ordered by level

	local j = 1
	while j <= #sortedSpellLevelReq do
		if (sortedSpellLevelReq[j] > player.level or player:hasSpell(sortedSpellYName[j])) then
			table.remove(sortedSpellItemReq, j)
			table.remove(sortedSpellItemAmount, j)
			table.remove(sortedSpellDesc, j)
			table.remove(sortedSpellName, j)
			table.remove(sortedSpellYName, j)
			table.remove(sortedSpellDisplay, j)
			table.remove(sortedSpellLevelReq, j)
			j = j - 1
		end
		j = j + 1
	end

	local choice = player:menuSeq(
		"Kau telah mengurai pikiranmu dan memperluas potensimu. Takdirmu sedang menunggu, bukan? Rahasia mana yang ingin kau pelajari?",
		sortedSpellName,
		{}
	)

	--player:talk(0,spellDesc[choice])
	local choice2 = player:menuSeq(
		"Kau siap mempelajari " .. sortedSpellName[choice] .. ": " .. sortedSpellDesc[
			choice
		] .. " Bersumpahkah kau memakai rahasia ini hanya untuk kebaikan?",
		{"Ya", "Tidak"},
		{}
	)

	local items = ""
	local itemName = ""
	local txt = ""

	if sortedSpellItemReq[choice] ~= nil and #sortedSpellItemReq[choice] ~= 0 then
		for i = 1, #sortedSpellItemReq[choice] do
			--player:talk(0,"id is: "..sortedSpellItemReq[choice][i])
			id = sortedSpellItemReq[choice][i]

			--player:talk(0,"amount is: "..sortedSpellItemAmount[choice][i])
			amount = sortedSpellItemAmount[choice][i]
			itemName = Item(id).name

			if (id == 0) then
				--Gold
				items = items .. amount .. " gold, "
			else
				items = items .. itemName .. " (" .. amount .. "), "
			end
		end
		txt = "The fee to learn " .. sortedSpellName[choice] .. " is:\n" .. items .. "All must be in good condition."
	elseif next(sortedSpellItemReq[choice]) == nil then
		txt = "The fee to learn " .. sortedSpellName[choice] .. " is: FREE\n"
	end

	local choice3 = player:menuString(txt, {"Ya", "Tidak"}, {})

	if choice3 == "Ya" then
		if sortedSpellItemReq[choice] ~= nil and #sortedSpellItemReq[choice] ~= 0 then
			for i = 1, #sortedSpellItemReq[choice] do
				id = sortedSpellItemReq[choice][i]
				amount = sortedSpellItemAmount[choice][i]

				if (id > 0) then
					if player:hasItem(id, amount) ~= true then
						player:menuString(
							"Membayar apa yang kau inginkan adalah tanda pengabdian. Kembalilah kalau syaratnya sudah kau penuhi.",
							{}
						)
						return
					end
				else
					if player.money < amount then
						player:menuString(
							"Membayar apa yang kau inginkan adalah tanda pengabdian. Kembalilah kalau syaratnya sudah kau penuhi.",
							{}
						)
						return
					end
				end
			end
		end

		-- Successful check of items and gold at this point.. proceed to taking the items and gold

		if sortedSpellItemReq[choice] ~= nil and #sortedSpellItemReq[choice] ~= 0 then
			for i = 1, #sortedSpellItemReq[choice] do
				id = sortedSpellItemReq[choice][i]
				amount = sortedSpellItemAmount[choice][i]

				if (id > 0) then
					player:removeItem(id, amount, 9)
				else
					player:removeGold(amount)
				end
			end
		end

		player:addSpell(sortedSpellYName[choice])
		player:sendMinitext("Pikiranmu meluas saat kau mempelajari " .. sortedSpellName[choice])
	elseif choice3 == "Tidak" then
		player:dialogSeq(
			{
				t,
				"Kemungkinan untuk belajar tidak ada habisnya; tetaplah rendah hati dan siap belajar."
			},
			1
		)
		return
	end
end

function Player.learnWisdomSpells(player, npc, spells)
	local t = {
		graphic = convertGraphic(npc.look, "monster"),
		color = npc.lookColor
	}
	player.npcGraphic = t.graphic
	player.npcColor = t.color
	player.dialogType = 0
	player.lastClick = npc.ID

	if next(spells) == nil then
		return
	end

	local spellYName = {}
	local spellName = {}
	local spellName2 = {}
	local spellYName2 = {}

	for i = 1, #spells do
		table.insert(spellYName, spells[i])
		table.insert(spellName, player:getSpellNameFromYName(spells[i]))
	end

	local spellLevelReq = {}
	local spellItemReq = {}
	local spellItemAmount = {}
	local spellDesc = {}
	local spellDisplay = {}

	for i = 1, #spellYName do
		local level = 1
		local items = {}
		local amounts = {}
		local desc = {}
		local func = assert(loadstring("return " .. spellYName[i] .. ".requirements"))(player)

		if (func ~= nil) then
			level, items, amounts, desc = func(player)
		end

		level = _getRequiredLevel(level)

		for j = 1, #items do
			if type(items[j]) == "string" then
				items[j] = Item(items[j]).id
			end
		end

		table.insert(spellLevelReq, level)
		table.insert(spellItemReq, items)
		table.insert(spellItemAmount, amounts)
		table.insert(spellDesc, desc)
		table.insert(spellDisplay, spellName[i] .. " Lvl: " .. spellLevelReq[i])
	end

	local sortedSpellName = sort_relative(spellLevelReq, spellName)
	local sortedSpellYName = sort_relative(spellLevelReq, spellYName)
	local sortedSpellItemReq = sort_relative(spellLevelReq, spellItemReq)
	local sortedSpellItemAmount = sort_relative(spellLevelReq, spellItemAmount)
	local sortedSpellDesc = sort_relative(spellLevelReq, spellDesc)
	local sortedSpellDisplay = sort_relative(spellLevelReq, spellDisplay)
	local sortedSpellLevelReq = sort_relative(spellLevelReq, spellLevelReq)

	--- Up to this point all spells ordered by level

	local j = 1
	while j <= #sortedSpellLevelReq do
		if (sortedSpellLevelReq[j] > player.level or player:hasSpell(sortedSpellYName[j])) then
			table.remove(sortedSpellItemReq, j)
			table.remove(sortedSpellItemAmount, j)
			table.remove(sortedSpellDesc, j)
			table.remove(sortedSpellName, j)
			table.remove(sortedSpellYName, j)
			table.remove(sortedSpellDisplay, j)
			table.remove(sortedSpellLevelReq, j)
			j = j - 1
		end
		j = j + 1
	end

	local choice = player:menuSeq(
		"Kau telah mengurai pikiranmu dan memperluas potensimu. Takdirmu sedang menunggu, bukan? Rahasia mana yang ingin kau pelajari?",
		sortedSpellName,
		{}
	)

	--player:talk(0,spellDesc[choice])
	local choice2 = player:menuSeq(
		"Kau siap mempelajari " .. sortedSpellName[choice] .. ": " .. sortedSpellDesc[
			choice
		] .. " Bersumpahkah kau memakai rahasia ini hanya untuk kebaikan?",
		{"Ya", "Tidak"},
		{}
	)

	local items = ""
	local itemName = ""
	local txt = ""

	if sortedSpellItemReq[choice] ~= nil and #sortedSpellItemReq[choice] ~= 0 then
		for i = 1, #sortedSpellItemReq[choice] do
			--player:talk(0,"id is: "..sortedSpellItemReq[choice][i])
			id = sortedSpellItemReq[choice][i]

			--player:talk(0,"amount is: "..sortedSpellItemAmount[choice][i])
			amount = sortedSpellItemAmount[choice][i]
			itemName = Item(id).name

			if (id == 0) then
				--Gold
				items = items .. amount .. " gold, "
			else
				items = items .. itemName .. " (" .. amount .. "), "
			end
		end
		txt = "The fee to learn " .. sortedSpellName[choice] .. " is:\n" .. items .. "All must be in good condition."
	elseif next(sortedSpellItemReq[choice]) == nil then
		txt = "The fee to learn " .. sortedSpellName[choice] .. " is: FREE\n"
	end

	local choice3 = player:menuString(txt, {"Ya", "Tidak"}, {})

	if choice3 == "Ya" then
		if sortedSpellItemReq[choice] ~= nil and #sortedSpellItemReq[choice] ~= 0 then
			for i = 1, #sortedSpellItemReq[choice] do
				id = sortedSpellItemReq[choice][i]
				amount = sortedSpellItemAmount[choice][i]

				if (id > 0) then
					if player:hasItem(id, amount) ~= true then
						player:menuString(
							"Membayar apa yang kau inginkan adalah tanda pengabdian. Kembalilah kalau syaratnya sudah kau penuhi.",
							{}
						)
						return
					end
				else
					if player.money < amount then
						player:menuString(
							"Membayar apa yang kau inginkan adalah tanda pengabdian. Kembalilah kalau syaratnya sudah kau penuhi.",
							{}
						)
						return
					end
				end
			end
		end

		-- Successful check of items and gold at this point.. proceed to taking the items and gold

		if os.time() < player.registry["learnWisdomSpellTimer"] then
			player:dialogSeq(
				{
					t,
					"Waktunya belum cukup sejak terakhir kau mempelajari mantra wisdom."
				},
				0
			)
			return
		end

		if sortedSpellItemReq[choice] ~= nil and #sortedSpellItemReq[choice] ~= 0 then
			for i = 1, #sortedSpellItemReq[choice] do
				id = sortedSpellItemReq[choice][i]
				amount = sortedSpellItemAmount[choice][i]

				if (id > 0) then
					player:removeItem(id, amount, 9)
				else
					player:removeGold(amount)
				end
			end
		end
		player.registry["learnWisdomSpellTimer"] = os.time() + 2592000

		-- 30 days
		player:addSpell(sortedSpellYName[choice])
		player:sendMinitext("Pikiranmu meluas saat kau mempelajari " .. sortedSpellName[choice])
	elseif choice3 == "Tidak" then
		player:dialogSeq(
			{
				t,
				"Kemungkinan untuk belajar tidak ada habisnya; tetaplah rendah hati dan siap belajar."
			},
			1
		)
		return
	end
end

function Player.forgetSpell(player, npc)
	local t = {
		graphic = convertGraphic(npc.look, "monster"),
		color = npc.lookColor
	}
	player.npcGraphic = t.graphic
	player.npcColor = t.color
	player.dialogType = 0
	player.lastClick = npc.ID

	local spellNames = player:getSpellName()
	local spellYNames = player:getSpellYName()
	local selection

	selection = player:menuSeq(
		"Rahasia mana yang ingin kau hapus dari pikiranmu?",
		spellNames,
		{}
	)

	choice = player:menuString(
		"Kau yakin ingin melupakan " .. spellNames[selection] .. "?",
		{"Ya", "Tidak"}
	)

	if (choice == "Ya") then
		player:removeSpell(spellYNames[selection])
		player:sendMinitext("Kau melupakan mantra " .. spellNames[selection])
	end
end

function Player.currentFutureSpells(player, npc)
	local t = {
		graphic = convertGraphic(npc.look, "monster"),
		color = npc.lookColor
	}
	player.npcGraphic = t.graphic
	player.npcColor = t.color
	player.dialogType = 0
	player.lastClick = npc.ID

	local spellYName = {}
	local spellName = {}
	local spellName2 = {}
	local spellYName2 = {}
	local unknownSpells = player:getUnknownSpells(
		player.baseClass,
		player.class
	)

	local spellLevelReq = {}
	local spellItemReq = {}
	local spellItemAmount = {}
	local spellDesc = {}
	local spellDisplay = {}

	for i = 1, #unknownSpells do
		if (i % 2 == 0) then
			table.insert(spellYName, unknownSpells[i])
		elseif (i % 2 == 1) then
			table.insert(spellName, unknownSpells[i])
		end
	end

	for i = 1, #spellYName do
		if spellYName[i] ~= "" then
			table.insert(spellYName2, spellYName[i])
			table.insert(spellName2, spellName[i])
		end
	end

	for i = 1, #spellYName2 do
		local level = 1
		local items = {}
		local amounts = {}
		local desc = {}
		local func = assert(loadstring("return " .. spellYName2[i] .. ".requirements"))(player)

		if (func ~= nil) then
			level, items, amounts, desc = func(player)
		end

		level = _getRequiredLevel(level)

		for j = 1, #items do
			if type(items[j]) == "string" then
				items[j] = Item(items[j]).id
			end
		end

		table.insert(spellLevelReq, level)
		table.insert(spellItemReq, items)
		table.insert(spellItemAmount, amounts)
		table.insert(spellDesc, desc)
		table.insert(
			spellDisplay,
			spellName2[i] .. " Lvl: " .. spellLevelReq[i]
		)
	end

	local sortedSpellName = sort_relative(spellLevelReq, spellName2)
	local sortedSpellYName = sort_relative(spellLevelReq, spellYName2)
	local sortedSpellItemReq = sort_relative(spellLevelReq, spellItemReq)
	local sortedSpellItemAmount = sort_relative(spellLevelReq, spellItemAmount)
	local sortedSpellDesc = sort_relative(spellLevelReq, spellDesc)
	local sortedSpellDisplay = sort_relative(spellLevelReq, spellDisplay)
	local sortedSpellLevelReq = sort_relative(spellLevelReq, spellLevelReq)

	--- Up to this point all spells ordered by level

	local j = 1
	while j <= #sortedSpellLevelReq do
		if (sortedSpellLevelReq[j] - player.level > 10 or player:hasSpell(sortedSpellYName[j])) then
			table.remove(sortedSpellItemReq, j)
			table.remove(sortedSpellItemAmount, j)
			table.remove(sortedSpellDesc, j)
			table.remove(sortedSpellName, j)
			table.remove(sortedSpellYName, j)
			table.remove(sortedSpellDisplay, j)
			table.remove(sortedSpellLevelReq, j)
			j = j - 1
		end
		j = j + 1
	end

	local choice = player:menuSeq(
		"Mantra mana yang ingin kau ketahui lebih lanjut?",
		sortedSpellName,
		{}
	)

	player:dialogSeq(
		{
			t,
			"<b>" .. sortedSpellName[choice] .. "\n\n" .. sortedSpellDesc[
				choice
			] .. " Bisa dipelajari pada Level " .. sortedSpellLevelReq[choice]
		},
		1
	)

	local items = ""
	local itemName = ""
	local txt = ""

	if sortedSpellItemReq[choice] ~= nil and #sortedSpellItemReq[choice] ~= 0 then
		for i = 1, #sortedSpellItemReq[choice] do
			id = sortedSpellItemReq[choice][i]
			amount = sortedSpellItemAmount[choice][i]
			itemName = Item(id).name

			if (id == 0) then
				--Gold
				items = items .. amount .. " gold, "
			else
				items = items .. itemName .. " (" .. amount .. "), "
			end
		end
		txt = "The fee to learn " .. sortedSpellName[choice] .. " is:\n" .. items .. "All must be in good condition."
	elseif next(sortedSpellItemReq[choice]) == nil then
		txt = "The fee to learn " .. sortedSpellName[choice] .. " is: FREE\n"
	end

	player:dialogSeq({t, txt}, 0)
end

function Player.futureSpells(player, npc, additionalSpells)
	local t = {
		graphic = convertGraphic(npc.look, "monster"),
		color = npc.lookColor
	}
	player.npcGraphic = t.graphic
	player.npcColor = t.color
	player.dialogType = 0
	player.lastClick = npc.ID

	local spellYName = {}
	local spellName = {}
	local spellName2 = {}
	local spellYName2 = {}
	local unknownSpells = player:getUnknownSpells(
		player.baseClass,
		player.class
	)

	local spellLevelReq = {}
	local spellItemReq = {}
	local spellItemAmount = {}
	local spellDesc = {}
	local spellDisplay = {}

	for i = 1, #unknownSpells do
		if (i % 2 == 0) then
			table.insert(spellYName, unknownSpells[i])
		elseif (i % 2 == 1) then
			table.insert(spellName, unknownSpells[i])
		end
	end

	for i = 1, #spellYName do
		if spellYName[i] ~= "" then
			table.insert(spellYName2, spellYName[i])
			table.insert(spellName2, spellName[i])
		end
	end

	if additionalSpells ~= nil then
		for i = 1, #additionalSpells do
			table.insert(spellYName2, additionalSpells[i])
			local name = player:getSpellNameFromYName(additionalSpells[i])
			table.insert(spellName2, name)
		end
	end

	for i = 1, #spellYName2 do
		local level = 1
		local items = {}
		local amounts = {}
		local desc = {}
		local func = assert(loadstring("return " .. spellYName2[i] .. ".requirements"))(player)

		if (func ~= nil) then
			level, items, amounts, desc = func(player)
		end

		level = _getRequiredLevel(level)

		for j = 1, #items do
			if type(items[j]) == "string" then
				items[j] = Item(items[j]).id
			end
		end

		table.insert(spellLevelReq, level)
		table.insert(spellItemReq, items)
		table.insert(spellItemAmount, amounts)
		table.insert(spellDesc, desc)
		table.insert(
			spellDisplay,
			spellName2[i] .. " Lvl: " .. spellLevelReq[i]
		)
	end

	local sortedSpellName = sort_relative(spellLevelReq, spellName2)
	local sortedSpellYName = sort_relative(spellLevelReq, spellYName2)
	local sortedSpellItemReq = sort_relative(spellLevelReq, spellItemReq)
	local sortedSpellItemAmount = sort_relative(spellLevelReq, spellItemAmount)
	local sortedSpellDesc = sort_relative(spellLevelReq, spellDesc)
	local sortedSpellDisplay = sort_relative(spellLevelReq, spellDisplay)
	local sortedSpellLevelReq = sort_relative(spellLevelReq, spellLevelReq)

	--- Up to this point all spells ordered by level

	local j = 1
	while j <= #sortedSpellLevelReq do
		if (sortedSpellLevelReq[j] <= player.level or sortedSpellLevelReq[j] - player.level > 10 or player:hasSpell(sortedSpellYName[j])) then
			table.remove(sortedSpellItemReq, j)
			table.remove(sortedSpellItemAmount, j)
			table.remove(sortedSpellDesc, j)
			table.remove(sortedSpellName, j)
			table.remove(sortedSpellYName, j)
			table.remove(sortedSpellDisplay, j)
			table.remove(sortedSpellLevelReq, j)
			j = j - 1
		end
		j = j + 1
	end

	local choice = player:menuSeq(
		"Mantra mana yang ingin kau ketahui lebih lanjut?",
		sortedSpellName,
		{}
	)

	player:dialogSeq(
		{
			t,
			"<b>" .. sortedSpellName[choice] .. "\n\n" .. sortedSpellDesc[
				choice
			] .. " Bisa dipelajari pada Level " .. sortedSpellLevelReq[choice]
		},
		1
	)

	local items = ""
	local itemName = ""
	local txt = ""

	if sortedSpellItemReq[choice] ~= nil and #sortedSpellItemReq[choice] ~= 0 then
		for i = 1, #sortedSpellItemReq[choice] do
			id = sortedSpellItemReq[choice][i]
			amount = sortedSpellItemAmount[choice][i]
			itemName = Item(id).name

			if (id == 0) then
				--Gold
				items = items .. amount .. " gold, "
			else
				items = items .. itemName .. " (" .. amount .. "), "
			end
		end
		txt = "The fee to learn " .. sortedSpellName[choice] .. " is:\n" .. items .. "All must be in good condition."
	elseif next(sortedSpellItemReq[choice]) == nil then
		txt = "The fee to learn " .. sortedSpellName[choice] .. " is: FREE\n"
	end

	player:dialogSeq({t, txt}, 0)
end

function Player.checkItems(player, items, amounts)
	for x = 1, #items do
		if (items[x] == 0) then
			if (player.money < amounts[x]) then
				return false
			end
		else
			if (player:hasItem(items[x], amounts[x]) == true) then
			else
				return false
			end
		end
	end

	return true
end

function Player.removeItems(player, items, amounts)
	for x = 1, #items do
		if (items[x] == 0) then
			player.money = player.money - amounts[x]
			player:sendStatus()
		else
			player:removeItem(items[x], amounts[x])
		end
	end
end

function Player.canLearnSpell(player, str)
	if (type(str) ~= "string") then
		return false
	end
	local spells = player:getSpells()
	if (#spells < 52) then
		if (player:hasSpell("" .. str)) then
			return false
		end
		return true
	else
		return false
	end
end
