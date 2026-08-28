SageNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {}

		if player.level < 50 then
			player:dialogSeq(
				{t, "Kembalilah kalau kau sudah mencapai pencerahan ke-50."},
				0
			)
			return
		end

		local sages = {
			"share_wisdom",
			"mentors_wisdom",
			"apprentices_wisdom",
			"adepts_wisdom",
			"sages_wisdom"
		}
		local sageCosts = {25000, 100000, 100000, 100000, 100000}
		local levelReqs = {50, 90, 90, 90, 90}
		local learnSageTimers = {
			604800 * 2,
			604800 * 4,
			604800 * 6,
			604800 * 8,
			0
		}

		-- 2 weeks, 4 weeks, 6 weeks, 8 weeks, reset back to 0

		if os.time() < player.registry["learnSageSpellTimer"] then
			player:dialogSeq(
				{
					t,
					"Untuk sekarang mantramu sudah ada, dan aku tidak akan mengizinkanmu meningkatkannya lagi selama " .. playerTimerValues(
						player,
						"learnSageSpellTimer"
					)
				},
				0
			)
			return
		end

		--[[player:dialogSeq({t,"Read the following rules very carefully, for if you should break one then you will lose this spell for a long time!",
		"Share wisdom is for you to share your wisdom with the community.",
		"Use of the spell in any way to offend anybody in the game can result in its loss.",
		"Sharing of wisdom not needed to the world of RetroTK can result in loss of this spell.",
		"Repeated spamming of your wisdom to the world can result in the loss of this spell.",
		"Jailing for ANY crime will result in loss of this spell.",
		"Breaking any other law in RetroTK using this spell will result in loss of the spell."},1)]]
		--

		player:dialogSeq(
			{
				t,
				"Baca aturan berikut baik-baik, sebab kalau kau melanggarnya, mantra ini akan hilang untuk waktu yang lama!",
				"Share wisdom dipakai untuk membagikan kebijaksanaanmu kepada masyarakat.",
				"Memakai mantra ini dengan cara apa pun untuk menyinggung atau mengganggu siapa pun dalam permainan akan membuatnya hilang.",
				"Menyebarkan kebijaksanaanmu berulang-ulang ke seluruh dunia bisa membuatmu kehilangan mantra ini.",
				"Jangan bawa perselisihan pribadi ke dalam sage.",
				"Dipenjara karena kejahatan APA PUN akan membuatmu kehilangan mantra ini.",
				"Melanggar hukum lain di RetroTK dengan mantra ini akan membuat mantranya hilang."
			},
			1
		)

		if player:hasSpell("sages_wisdom") then
			player:dialogSeq(
				{t, "Aku sudah mengajarkanmu tingkat kebijaksanaan tertinggi"},
				0
			)
			return
		end

		local choice = player:menuSeq(
			"Kau memahami aturan ini sepenuhnya?",
			{
				"Ya, aku memahami dan menerima aturannya.",
				"Tidak, tolong ulangi untukku."
			},
			{}
		)

		local sage = ""
		local sageCost = 0
		local levelReq = 0
		local learnSageTimer = 0

		if choice == 1 then
			-- accept

			for i = 1, #sages do
				if player:hasSpell(sages[i]) then
					sage = sages[i + 1]
					sageCost = sageCosts[i + 1]
					levelReq = levelReqs[i + 1]
					learnSageTimer = learnSageTimers[i + 1]
					break
				end
			end

			if sage == "" then
				-- no spell found
				sage = sages[1]
				sageCost = sageCosts[1]
				levelReq = levelReqs[1]
				learnSageTimer = learnSageTimers[1]
			end

			if sageCost == 0 then
				return
			end

			local choice2 = player:menuSeq(
				"Mantra ini berharga " .. Tools.formatNumber(sageCost) .. " emas untuk dipelajari.",
				{"Ya, uangnya ada padaku.", "Tidak, aku tidak akan membayar."},
				{}
			)

			if choice2 == 1 then
				-- have money
				if player.money < sageCost then
					player:dialogSeq(
						{
							t,
							"Mantra yang akan kuajarkan berharga " .. Tools.formatNumber(sageCost) .. " emas. Temui aku lagi kalau kau sudah punya."
						},
						0
					)
					return
				end

				player:removeGold(sageCost)
				player:addSpell(sage)

				player.registry["learnSageSpellTimer"] = os.time() + learnSageTimer

				player:dialogSeq(
					{
						t,
						"Pakai mantramu baik-baik; penyalahgunaan akan membuatnya hilang, dan kau harus mempelajarinya lagi dari awal."
					},
					0
				)
			elseif choice2 == 2 then
				-- no
				return
			end
		elseif choice == 2 then
			-- repeat
			player:freeAsync()
			SageNpc.click(player, npc)
		end
	end)
}
