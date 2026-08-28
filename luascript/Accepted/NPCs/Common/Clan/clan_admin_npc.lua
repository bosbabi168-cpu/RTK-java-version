clanRankNames = {
	"Initiate",
	"Member",
	"Council (no bank access)",
	"Council (item banking)",
	"Council (full banking)",
	"Primarch",
	"Primogen"
}

ClanAdminNpc = {
	click = async(function(player, npc, speech)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Clan roster", "Add member"}

		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Clan roster" then
			ClanAdminNpc.clanRoster(player, npc, player.clan)
		elseif menu == "Add member" then
			ClanAdminNpc.addMember(player, npc, player.clan)
		end
	end),

	addMember = function(player, npc, clanid)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local input = player:inputLetterCheck(player:inputSeq(
			"Who would you like to add to the clan?",
			"The noble",
			"is welcome in my clan.",
			{},
			{}
		))

		local target = Player(input)

		if target == nil then
			player:dialogSeq({t, "Pemain tidak daring."}, 0)
			return
		end

		--if not distanceSquare(player,target,10) then player:dialogSeq({t,"Player must be near you."},0) return end

		if target.ID == player.ID then
			return
		end

		if target.clan ~= 0 then
			player:dialogSeq(
				{
					t,
					"Anggota itu sedang menjadi bagian klan lain. Ia harus keluar dari klan itu sebelum bisa bergabung dengan klanmu."
				},
				0
			)
			return
		end

		target:freeAsync()
		ClanAdminNpc.addMemberAsk(target, player, npc, clanid)
	end,

	addMemberAsk = async(function(player, asker, npc, clanid)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local clanName = getClanName(clanid)

		local choice = player:menuSeq(
			asker.name .. " mengundangmu bergabung dengan klan " .. clanName .. ". Kau ingin bergabung?",
			{"Ya, gabung.", "Tidak, aku tidak mau bergabung."},
			{}
		)

		if choice == 1 then
			asker:freeAsync()
			ClanAdminNpc.addMemberConfirmed(asker, player, npc, clanid)
		elseif choice == 2 then
			asker:msg(
				4,
				player.name .. " has rejected your clan invitation.",
				asker.ID
			)
		end
	end),

	addMemberConfirmed = async(function(player, newMember, npc, clanid)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local clanName = getClanName(clanid)
		addClanMember(newMember.ID, clanid)

		player:msg(
			0,
			newMember.name .. " has been added to your clan roster.",
			player.ID
		)
		newMember:msg(
			0,
			"Welcome to the " .. clanName .. " clan.",
			newMember.ID
		)
	end),

	clanRoster = function(player, npc, clanid)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local members = getClanRoster(clanid)
		local clanName = getClanName(clanid)

		local memberIds = {}
		local memberNames = {}
		local memberRanks = {}

		for i = 1, #members, 2 do
			table.insert(memberIds, members[i])
			table.insert(memberRanks, members[i + 1])
			table.insert(memberNames, getOfflineID(members[i]))
		end

		local sortedMemberIds = sort_relativeDesc(memberRanks, memberIds)
		local sortedMemberNames = sort_relativeDesc(memberRanks, memberNames)
		local sortedMemberRanks = sort_relativeDesc(memberRanks, memberRanks)

		local entries = {}

		for i = 1, #sortedMemberNames do
			local nameString = sortedMemberNames[i]
			local spaces = 0
			if #nameString < 16 then
				spaces = 16 - #nameString
			end

			for i = 1, spaces do
				nameString = nameString .. " "
			end

			if sortedMemberRanks[i] > 0 then
				table.insert(
					entries,
					nameString .. "  Rank: " .. clanRankNames[
						sortedMemberRanks[i]
					]
				)
			end
		end

		local choice = player:menuSeq(clanName .. " roster\n", entries, {})

		local chosenMemberId = sortedMemberIds[choice]
		local chosenMemberName = sortedMemberNames[choice]
		local chosenMemberRank = sortedMemberRanks[choice]

		if player.ID == chosenMemberId then
			player:dialogSeq({t, "Kau tidak bisa mengubah pangkatmu sendiri."}, 0)
			return
		end

		if chosenMemberRank >= player.clanRank then
			player:dialogSeq(
				{
					t,
					"Kau hanya bisa mengubah anggota yang pangkatnya di bawahmu."
				},
				0
			)
			return
		end

		local choice2 = player:menuString(
			"Apa yang ingin kau lakukan " .. clanRankNames[chosenMemberRank] .. " " .. chosenMemberName .. "?",
			{"Promote member", "Demote member", "Remove member"},
			{}
		)

		if choice2 == "Promote member" then
			if clanRankNames[chosenMemberRank + 1] == "Primogen" then
				player:dialogSeq(
					{
						t,
						"Anggota tidak bisa dinaikkan ke pangkat Primogen. Kau harus memakai fitur Mundur untuk menuntaskan tindakan ini."
					},
					0
				)
				return
			end

			local promote = player:menuSeq(
				"Kau akan menaikkan pangkat " .. chosenMemberName .. " dari " .. clanRankNames[
					chosenMemberRank
				] .. " ke " .. clanRankNames[chosenMemberRank + 1] .. ". Kau ingin melanjutkan?",
				{"Ya, naikkan pangkatnya.", "Tidak, lupakan saja."},
				{}
			)

			if promote == 1 then
				-- confirm
				updateClanMemberRank(chosenMemberId, chosenMemberRank + 1)

				if Player(chosenMemberId) ~= nil then
					-- player online
					Player(chosenMemberId):msg(
						0,
						"You have been promoted to the rank of " .. clanRankNames[
							chosenMemberRank + 1
						] .. " in your clan.",
						chosenMemberId
					)
				end

				player:dialogSeq(
					{
						t,
						chosenMemberName .. " telah dinaikkan ke pangkat " .. clanRankNames[
							chosenMemberRank + 1
						] .. " di klanmu."
					},
					0
				)
				return
			end
		elseif choice2 == "Demote member" then
			if chosenMemberRank - 1 <= 0 then
				player:dialogSeq(
					{
						t,
						"Anggota ini tidak bisa diturunkan lebih rendah dari pangkatnya sekarang."
					},
					0
				)
				return
			end

			local demote = player:menuSeq(
				"Kau akan menurunkan pangkat " .. chosenMemberName .. " dari " .. clanRankNames[
					chosenMemberRank
				] .. " ke " .. clanRankNames[chosenMemberRank - 1] .. ". Kau ingin melanjutkan?",
				{"Ya, turunkan pangkatnya.", "Tidak, lupakan saja."},
				{}
			)

			if demote == 1 then
				-- confirm
				updateClanMemberRank(chosenMemberId, chosenMemberRank - 1)

				if Player(chosenMemberId) ~= nil then
					-- player online
					Player(chosenMemberId):msg(
						0,
						"You have been demoted to the rank of " .. clanRankNames[
							chosenMemberRank - 1
						] .. " in the " .. clanName .. " clan.",
						chosenMemberId
					)
				end

				player:dialogSeq(
					{
						t,
						chosenMemberName .. " telah diturunkan ke pangkat " .. clanRankNames[
							chosenMemberRank - 1
						] .. " di " .. clanName .. " clan."
					},
					0
				)
				return
			end
		elseif choice2 == "Remove member" then
			local confirm = player:menuSeq(
				"Kau akan mengeluarkan " .. chosenMemberName .. " dari " .. clanName .. ". Kau ingin melanjutkan?",
				{"Ya, keluarkan dia.", "Tidak, lupakan saja."},
				{}
			)

			if confirm == 1 then
				removeClanMember(chosenMemberId)

				if Player(chosenMemberId) ~= nil then
					-- player online
					Player(chosenMemberId):msg(
						0,
						"You have been removed from " .. clanName .. ".",
						chosenMemberId
					)
				end

				player:dialogSeq(
					{
						t,
						chosenMemberName .. " telah dikeluarkan dari " .. clanName .. "."
					},
					0
				)
				return
			end
		end
	end,
}
