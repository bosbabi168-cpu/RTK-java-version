local _invalidShadowCountDialog = "It is impossible to exceed one's own potential."

local _bonHwaLimits = {
	{ -- Enchanted
		-- {Might, Grace, Will}
		{135, 130, 130}, -- Warrior
		{130, 135, 130}, -- Rogue
		{130, 130, 135}, -- Mage
		{130, 130, 135}  -- Poet
	},
	{ -- Il san
		{140, 135, 130},
		{135, 140, 130},
		{130, 135, 140},
		{135, 130, 140}
	},
	{ -- Ee san
		{150, 140, 130},
		{140, 150, 130},
		{130, 140, 150},
		{136, 130, 150}
	},
	{ -- Sam san
		{150, 145, 130},
		{145, 150, 130},
		{130, 145, 150},
		{139, 130, 150}
	},
	{ -- Sa san
		{150, 150, 130},
		{150, 150, 130},
		{130, 150, 150},
		{142, 130, 150}
	},
}

local _showInsufficientExp = function(player, cost)
	player:dialogSeq({"Kau belum cukup memahami hakikat dirimu untuk melepaskan potensimu lebih jauh. Kembalilah kalau kau sudah memiliki setidaknya " .. Tools.formatNumber(cost) .. " experience."}, 1)
end

local _promptShadowCount = function(player, statLabel, baseStatValue, maxShadowsPossible)
	local shadowCount = player:inputNumberCheck(
		player:input("" .. statLabel .. "-mu yang alami adalah " .. Tools.formatNumber(baseStatValue) .. ".\n\nKau bisa melepaskan potensi bayanganmu sampai " .. Tools.formatNumber(maxShadowsPossible) .. " kali.\n\nBerapa kali yang kau pilih?")
	)

	return shadowCount
end

local _confirmShadowCount = function(player, statLabel, newStatValue, expCost)
	local confirmation = player:menuString(
		"" .. statLabel .. " akan naik secara permanen menjadi " .. Tools.formatNumber(newStatValue) .. ".\n\n" .. Tools.formatNumber(expCost) .. " pengalaman akan dikorbankan tanpa bisa ditarik kembali.\n\nKau yakin?",
		{"Ya", "Tidak"},
		{}
	)

	return confirmation
end

local _calculateBaseStats = function(player)
	player:calcStat()
	player:sendStatus()
end

local _finalizeExpSale = function(player)
	player:sendAnimation(18)
	player:playSound(708)
	_calculateBaseStats(player)
end

local _awardBonuses = function(player, nextCost, iterations)
	local smallExpBonus = math.ceil(nextCost / 20)
	local bigExpBonus = math.ceil(nextCost / 10)

	local bonusExp = 0
	local bonusKarma = 0

	for _ = 1, iterations do
		local rand = math.random(100)

		if (rand == 1) then
			bonusExp = bonusExp + bigExpBonus
			bonusKarma = bonusKarma + 0.01
		elseif (rand > 95) then
			bonusExp = bonusExp + smallExpBonus
		end
	end

	if (bonusExp > 0) then
		player.exp = player.exp + bonusExp
		player:addKarma(bonusKarma)
		player:sendStatus()
		player:dialogSeq({"Latihanmu sangat efisien. Biayanya " .. Tools.formatNumber(bonusExp) .. " experience less than expected."})
	end
end

local _shadowStat = function(player, statIndex, statMaxValue, statCost)
	local smallReactions = {"Your muscles feel a surge.", "You feel more fluid.", "Your mind expands."}
	local bigReactions = {"Your muscles scream.", "Your nerves scream.", "Your mind screams."}
	local statLabels = {"Might", "Grace", "Will"}

	local statLabel = statLabels[statIndex]
	local baseStatValues = {player.baseMight, player.baseGrace, player.baseWill}
	local baseStatValue = baseStatValues[statIndex]

	player.registry["base" .. statLabel] = baseStatValue
	_calculateBaseStats(player)

	local maxShadowsPossible = math.floor(player.exp / statCost)
	local maxShadowsAllowed = statMaxValue - baseStatValue
	local maxShadows = math.min(maxShadowsPossible, maxShadowsAllowed)

	local shadowCount = _promptShadowCount(player, statLabel, baseStatValue, maxShadows)

	if (shadowCount > maxShadows) then
		player:dialogSeq({_invalidShadowCountDialog})
		return
	end

	local newStatValue = baseStatValue + shadowCount
	local expCost = shadowCount * statCost

	local confirmation = _confirmShadowCount(player, statLabel, newStatValue, expCost)

	if (confirmation ~= "Ya") then
		return
	end

	player.exp = player.exp - expCost
	player.expSoldStats = player.expSoldStats + expCost
	player.registry["base" .. statLabel] = newStatValue -- These registry assignments look pointless, but I think they are required for player:calcStat() to work properly.

	if (statIndex == 1) then
		player.baseMight = newStatValue
	elseif (statIndex == 2) then
		player.baseGrace = newStatValue
	else
		player.baseWill = newStatValue
	end

	local reaction = smallReactions[statIndex]

	if (shadowCount > 9) then
		reaction = bigReactions[statIndex]
	end

	player:sendMinitext(reaction)
	characterLog.xpSellWrite(player, statLabel:lower(), shadowCount, expCost)

	_finalizeExpSale(player)
	_awardBonuses(player, statCost, shadowCount)
end

local _getVitaOrManaCost = function(currentValue, statIndex)
	local minimumCost = 20000000
	local calculatedCost = math.floor((currentValue * statIndex - 80000 * Config.expSellFactor1) / 20000) * 2000000 * Config.expSellFactor2 + 20000000
	local cost = math.max(minimumCost, calculatedCost)

	return cost
end

local _shadowVitaOrMana = function(player, statIndex)
	local reactions = {"Your body strengthens.", "Your mind strengthens."}

	local isMinor = player.level < 99
	local statValueCap = 0

	if (isMinor) then
		statValueCap = 10000 / statIndex
	end

	local shadowsPossible = 0
	local exp = player.exp
	local statInterval = 100 / statIndex
	local currentValue = player.baseHealth
	local statLabel = "Vitality"

	if (statIndex == 2) then
		currentValue = player.baseMagic
		statLabel = "Mana"
	end

	player.registry["base" .. statLabel] = currentValue
	_calculateBaseStats(player)

	local tempValue = currentValue
	local tempCost

	while (exp > 0) do
		local nextValue = tempValue + statInterval

		if (isMinor and nextValue > statValueCap) then
			break
		end

		tempCost = _getVitaOrManaCost(tempValue, statIndex)

		if (exp >= tempCost) then
			shadowsPossible = shadowsPossible + 1
		end

		exp = exp - tempCost
		tempValue = nextValue
	end

	if (shadowsPossible < 1) then
		if (isMinor and statValueCap - currentValue < statInterval) then
			player:dialogSeq({"Untuk sekarang kau sudah mencapai batasmu, anak muda. Kembalilah kepadaku kalau kau sudah meraih pencerahan terakhir."})
		end

		_showInsufficientExp(player, tempCost)
	end

	local shadowCount = _promptShadowCount(player, statLabel, currentValue, shadowsPossible)

	if (shadowCount > shadowsPossible) then
		player:dialogSeq({_invalidShadowCountDialog})
		return
	end

	local expCost = 0
	local newValue = currentValue

	for _ = 1, shadowCount do
		expCost = expCost + _getVitaOrManaCost(newValue, statIndex)
		newValue = newValue + statInterval
	end

	local confirmed = _confirmShadowCount(player, statLabel, newValue, expCost)

	if (confirmed ~= "Ya") then
		return
	end

	player.exp = player.exp - expCost
	player.registry["base" .. statLabel] = newValue

	if (statIndex == 1) then
		player.expSoldHealth = player.expSoldHealth + expCost
		player.baseHealth = newValue
	else
		player.expSoldMagic = player.expSoldMagic + expCost
		player.baseMagic = newValue
	end

	player:sendMinitext(reactions[statIndex])
	characterLog.xpSellWrite(player, statLabel:lower(), statInterval, expCost)

	_finalizeExpSale(player)

	local nextCost = _getVitaOrManaCost(newValue, statIndex)
	_awardBonuses(player, nextCost, shadowCount)
end

ExpSellerNpc = {
	click = async(function(player, npc)
		ExpSellerNpc.showShadowMainMenu(player, npc)
	end),

	getManaCost = function(currentValue)
		return _getVitaOrManaCost(currentValue, 2)
	end,

	getVitaCost = function(currentValue)
		return _getVitaOrManaCost(currentValue, 1)
	end,

	showShadowMainMenu = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}

		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if (player.m ~= npc.m) then
			return
		end

		if player.level < 90 then
			player:dialogSeq(
				{t, "Tidak ada yang bisa kulakukan untukmu, anak muda. Kembalilah kalau kau sudah meraih pencerahan ke-90."},
				0
			)
			return
		end

		local opts = {"Shadow Stats", "Shadow Vitality", "Shadow Mana"}

		local choice = player:menuSeq(
			"Selamat datang, yang agung. Ada yang bisa saya layani?",
			opts,
			{}
		)

		if (choice == 1) then
			ExpSellerNpc.showShadowStatsMenu(player, npc)
		elseif (choice == 2) then
			_shadowVitaOrMana(player, 1)
		elseif (choice == 3) then
			_shadowVitaOrMana(player, 2)
		end
	end,

	showShadowStatsMenu = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}

		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local statMaxValues = {130, 130, 130}
		local statCost = 10000000
		local npcIsBonHwa = npc.name == "Bon-Hwa"

		if (npcIsBonHwa and (player.baseHealth >= 80000 or player.baseMagic >= 40000)) then
			local markLimits = _bonHwaLimits[player.mark + 1]
			statMaxValues = markLimits[player.baseClass]
			statCost = statCost * 10
		end

		if (player.exp < statCost) then
			_showInsufficientExp(player, statCost)
			return
		end

		local opts = {}

		if (player.baseMight < statMaxValues[1]) then
			table.insert(opts, "Might")
		end

		if (player.baseGrace < statMaxValues[2]) then
			table.insert(opts, "Grace")
		end

		if (player.baseWill < statMaxValues[3]) then
			table.insert(opts, "Will")
		end

		if (#opts < 1) then
			local dialog = "There is nothing more I can do for you. Perhaps you can find another who can guide you further."

			if (npcIsBonHwa) then
				dialog = "You have already realized your full potential."
			end

			player:dialogSeq({t, dialog}, 0)
			return
		end

		local choice = player:menuString(
			"Sisi potensimu yang mana yang ingin kau lepaskan?",
			opts,
			{}
		)

		local statIndex

		if (choice == "Might") then
			statIndex = 1
		elseif (choice == "Grace") then
			statIndex = 2
		elseif (choice == "Will") then
			statIndex = 3
		end

		if (statIndex ~= nil) then
			_shadowStat(player, statIndex, statMaxValues[statIndex], statCost)
		end
	end,

	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)

		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}

		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if speech == "kawlana" then
			Tools.checkKarma(player)

			if player.quest["wind_armor"] == 0 or player.quest["min_kawlana"] == 0 or not player:karmaCheck("spirit") then
				player:dialogSeq(
					{t, "Aku sungguh tidak paham apa yang kau bicarakan."},
					0
				)
				return
			end

			if player.quest["kawlana_quest"] == 0 then
				player:dialogSeq(
					{
						t,
						"Kau mencari Kawlana? Sudah lama sekali aku tidak mendengar kata itu.",
						"Bangsa yang memakai istilah itu sudah lama lenyap, pudar ditelan waktu, tak pernah terlihat lagi.",
						"Kawlana adalah jiwamu sendiri, keberadaanmu, daya hidupmu.",
						"Untuk mendapatkan yang kau cari, kau harus melepaskan sebagian daya hidupmu dan membotolkannya.",
						"Ritual ini sangat suci, dan sebelum menjalaninya kau harus bisa menunjukkan pemahaman atas kepercayaanmu pada segala yang kudus.",
						"Kalau kau masih ingin melakukannya, kau harus membawakan beberapa hal."
					},
					1
				)
				player.quest["kawlana_quest"] = 1

				player:dialogSeq(
					{
						t,
						"Kau butuh botol untuk menampungnya, sesuatu yang menyimpan daya menyembuhkan tubuh dari kejahatan.",
						"Kau butuh fine steel dagger untuk menyayat dadamu, supaya sari hidupmu mengalir ke dalam botol.",
						"Untuk menjaga ketepatan dan menghindari luka berlebih, kau butuh gelang grace.",
						"Butuh kekuatan besar untuk menancapkan belati cukup dalam hingga mencapai jantung. Kau butuh barang kekuatan untuk ini...",
						"Terakhir, kain penyembuh diperlukan untuk merawat lukamu, atau jiwamu akan terus berdarah sampai pudar.",
						"Bawa semua itu dan siapkan dirimu, sebab ini akan melemahkanmu dengan cara yang belum kau ketahui."
					},
					0
				)
				return
			end

			if player.quest["kawlana_quest"] == 1 then
				if player:hasItem("holy_ring", 1) ~= true or player:hasItem("indigo_potion", 1) ~= true or player:hasItem(
					"fine_steel_dagger",
					1
				) ~= true or player:hasItem("whisper_bracelet", 1) ~= true or player:hasItem(
					"titanium_glove",
					1
				) ~= true or player:hasItem("sen_glove", 1) ~= true then
					player:dialogSeq(
						{t, "Barang yang diperlukan belum lengkap."},
						0
					)
					return
				end

				player:dialogSeq(
					{
						t,
						"Ahh, semua yang kau butuhkan sudah ada, tetapi kau siap memulai?"
					},
					1
				)

				local choice = player:menuSeq(
					"Kau ingin mengambil Kawlana-mu sekarang?",
					{"Ya, aku mau.", "Tidak, aku tidak mau."},
					{}
				)

				if choice == 1 then
					--accept

					if os.time() < player.quest["kawlana_timer"] then
						player:dialogSeq(
							{
								t,
								"Jiwamu belum cukup kuat untuk yang kau minta. Kau harus pulih dan menguat sebelum mencoba mengambil Kawlana-mu.",
								"Kau punya " .. playerQuestTimerValues(
									player,
									"kawlana_timer"
								) .. " lagi sampai kau bisa memperoleh Kawlana berikutnya."
							},
							1
						)
						return
					end

					if player.baseHealth < 100 then
						player:dialogSeq(
							{
								t,
								"Vita-mu tidak cukup untuk melanjutkan ritual."
							},
							0
						)
						return
					end

					player:dialogSeq({t, "Maka upacaranya dimulai..."}, 1)

					player:dialogSeq(
						{
							t,
							"Dengan benda suci ini, hormat ditunjukkan kepada upacara."
						},
						1
					)
					player:removeItem("holy_ring", 1)

					player:dialogSeq(
						{t, "Dengan belati ini kau akan mengalirkan darah jiwamu."},
						1
					)
					player:removeItem("fine_steel_dagger", 1)

					player:dialogSeq(
						{
							t,
							"Dengan sarung tangan ini kau menemukan kekuatan menyayat dalam ke jiwamu."
						},
						1
					)
					player:removeItem("titanium_glove", 1)

					player:dialogSeq(
						{
							t,
							"Dengan gelang ini kau menemukan kecakapan menuntun bilahnya."
						},
						1
					)
					player:removeItem("whisper_bracelet", 1)

					player:dialogSeq(
						{
							t,
							"Dalam botol ini kau akan mengumpulkan kekuatan yang kau butuhkan untuk melindungi dan menyembuhkan."
						},
						1
					)
					player:removeItem("indigo_potion", 1)

					player:dialogSeq(
						{
							t,
							"Dengan sarung tangan kain ini kau merawat lukamu. Sekarang diamlah, selagi kekuatannya ditampung."
						},
						1
					)
					player:removeItem("sen_glove", 1)

					player.baseHealth = player.baseHealth - 100
					player:calcStat()
					player.quest["kawlana_timer"] = os.time() + 10800

					-- 3 real hrs

					player:addItem("kawlana", 1)
					player:dialogSeq(
						{
							t,
							"Lihatlah! Kau sudah mengumpulkan Kawlana-mu sendiri. Pergilah dan pulihkan dirimu. Tubuhmu melemah karena ujian ini."
						},
						0
					)
				elseif choice == 2 then
					-- no
					player:dialogSeq(
						{t, "Baiklah. Kembalilah kalau kau sudah siap."},
						0
					)
					return
				end
			end
		end
	end)
}
